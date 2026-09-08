package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.enums.VerifyType;
import com.ibigou.blindbox.repository.IbigouGoodsRepository;
import com.ibigou.blindbox.repository.IbigouOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 宜必购内部渠道服务（流程 G）。
 * <p>完全内部模块：总开关控制；资产过滤 can_use_after_draw=1 + is_support_ibigou=1；
 * 下单事务保证券核销、余额扣减原子性；全额退款券恢复、部分退款券作废。</p>
 */
@Service
@RequiredArgsConstructor
public class IbigouService {

    private final GlobalConfigService configService;
    private final IbigouGoodsRepository goodsRepository;
    private final IbigouOrderRepository orderRepository;
    private final CouponService couponService;
    private final BalanceService balanceService;
    private final FlowCloseService flowCloseService;
    private final AnnounceService announceService;

    /** 总开关校验 */
    public void checkChannelOpen() {
        if (!configService.ibigouChannelOpen()) {
            throw new BizException("渠道暂未开放");
        }
    }

    public List<IbigouGoods> listGoods() {
        checkChannelOpen();
        return goodsRepository.findByEnabledOrderBySortOrderDescSalesCountDesc(1);
    }

    /** 可用资产：过滤后的券 + 可用余额 + 抵扣比例 */
    public IbigouAssets availableAssets(String userPhone) {
        checkChannelOpen();
        return new IbigouAssets(
                couponService.availableForIbigou(userPhone),
                balanceService.availableBalance(userPhone),
                configService.balanceDeductRate());
    }

    /** 宜必购下单：券核销 verify_type=3，余额扣减 verify_type=3，事务原子 */
    @Transactional
    public IbigouOrder createOrder(String userPhone, Long couponId,
                                   BigDecimal deductBalance, BigDecimal orderAmount,
                                   String drawBatchNo) {
        checkChannelOpen();
        if (orderAmount == null || orderAmount.signum() <= 0) {
            throw new BizException("订单金额必须大于 0");
        }
        String bizNo = "IBG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        BigDecimal afterCoupon = orderAmount;
        Long usedCouponId = null;
        if (couponId != null) {
            UserCoupon coupon = couponService.verifyIbigou(couponId, bizNo);
            afterCoupon = CouponService.afterCoupon(coupon, orderAmount);
            usedCouponId = couponId;
        }
        BigDecimal limit = balanceService.deductLimit(afterCoupon, configService.balanceDeductRate());
        BigDecimal deducted = deductBalance == null ? BigDecimal.ZERO : deductBalance;
        String flowMerchantNo = usedCouponId != null ? couponService.get(usedCouponId).getSourceMerchantNo() : "PLATFORM";
        if (deducted.signum() > 0) {
            balanceService.deduct(userPhone, flowMerchantNo, deducted, limit, VerifyType.IBIGOU.getCode(), bizNo);
        }
        IbigouOrder order = new IbigouOrder();
        order.setIbigouOrderNo(bizNo);
        order.setUserPhone(userPhone);
        order.setMerchantNo(usedCouponId != null ? couponService.get(usedCouponId).getSourceMerchantNo() : "PLATFORM");
        order.setCouponId(usedCouponId);
        order.setDeductBalance(deducted);
        order.setOrderAmount(orderAmount);
        order.setPayAmount(afterCoupon.subtract(deducted).max(BigDecimal.ZERO));
        order.setRefundStatus(0);
        order.setRefundAmount(BigDecimal.ZERO);
        LocalDateTime now = LocalDateTime.now();
        order.setCreateTime(now);
        order.setPayTime(now);
        order.setUpdateTime(now);
        IbigouOrder saved = orderRepository.save(order);
        // 播报：商城订单创建成功 → 商家端语音播报（女声TTS），内容与顾客端 speakPay 提示一致
        try {
            BigDecimal payAmt = saved.getPayAmount() == null ? BigDecimal.ZERO : saved.getPayAmount();
            BigDecimal saveAmt = saved.getOrderAmount().subtract(payAmt).max(BigDecimal.ZERO);
            StringBuilder sb = new StringBuilder("宜必购盲盒。");
            if (saved.getOrderAmount() != null && saved.getOrderAmount().signum() > 0) {
                sb.append("订单金额").append(saved.getOrderAmount()).append("元。");
            }
            if (saveAmt.signum() > 0) {
                sb.append("优惠节省").append(saveAmt).append("元。");
            }
            sb.append("应付").append(payAmt).append("元。");
            sb.append("请选择支付方式完成付款。宜必购盲盒只是做优惠，不做收款，请商家查收是否真实付款成功，请注意。");
            announceService.record(saved.getMerchantNo(), "pay", sb.toString());
        } catch (Exception e) {
            // 播报失败不影响下单
            System.err.println("宜必购订单播报失败: " + e.getMessage());
        }
        // 流程闭环③：宜必购下单成功 → 本次盲盒产出所有奖品 can_use_after_draw=1
        if (drawBatchNo != null && !drawBatchNo.isBlank()) {
            flowCloseService.closeByDrawBatch(drawBatchNo);
        }
        return saved;
    }

    /** 宜必购退款：refundAmount 为 null 表示全额退款 */
    @Transactional
    public IbigouOrder refund(String ibigouOrderNo, BigDecimal refundAmount) {
        checkChannelOpen();
        IbigouOrder order = orderRepository.findById(ibigouOrderNo)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (order.getRefundStatus() != 0) {
            throw new BizException("订单已退款，不能重复退款");
        }
        boolean full = refundAmount == null;
        if (!full && (refundAmount.signum() <= 0 || refundAmount.compareTo(order.getPayAmount()) > 0)) {
            throw new BizException("退款金额不合法");
        }
        if (order.getCouponId() != null) {
            if (full) {
                couponService.restore(order.getCouponId());
            } else {
                couponService.voidCoupon(order.getCouponId());
            }
        }
        BigDecimal refunded = full ? order.getDeductBalance()
                : order.getDeductBalance().multiply(refundAmount)
                        .divide(order.getPayAmount(), 2, java.math.RoundingMode.DOWN);
        if (refunded.signum() > 0) {
            balanceService.refund(order.getUserPhone(), order.getMerchantNo(), refunded, ibigouOrderNo,
                    full ? "宜必购订单全额退款" : "宜必购订单部分退款");
        }
        order.setRefundStatus(full ? 1 : 2);
        order.setRefundAmount(refunded);
        order.setRefundTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return orderRepository.save(order);
    }

    /** 我的宜必购订单（按时间倒序） */
    public List<IbigouOrder> myOrders(String userPhone) {
        checkChannelOpen();
        return orderRepository.findByUserPhoneOrderByCreateTimeDesc(userPhone);
    }

    /** 可用资产 DTO */
    public record IbigouAssets(List<UserCoupon> coupons, BigDecimal balance, int balanceDeductRate) {
    }
}
