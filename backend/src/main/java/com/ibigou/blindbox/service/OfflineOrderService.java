package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.OfflineOrder;
import com.ibigou.blindbox.entity.UserCoupon;
import com.ibigou.blindbox.enums.VerifyType;
import com.ibigou.blindbox.repository.OfflineOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 线下门店自营订单（流程 E）。
 * <p>抵扣规则：优先优惠券（须发行商家匹配）→ 余额（上限 = 优惠后实付 × balance_deduct_rate）。
 * 全额退款：券恢复、余额全额退回；部分退款：券不退回、余额按比例退回。</p>
 * <p>流程 C1：抽奖后选择本店自营下单，支付成功触发本次抽奖批次闭环。</p>
 */
@Service
@RequiredArgsConstructor
public class OfflineOrderService {

    private final OfflineOrderRepository orderRepository;
    private final CouponService couponService;
    private final BalanceService balanceService;
    private final OrderCalcService orderCalcService;
    private final FlowCloseService flowCloseService;
    private final MemberService memberService;
    private final GlobalConfigService globalConfigService;
    private final AuditLogService auditLogService;
    private final AnnounceService announceService;
    private final UserMessageService userMessageService;

    /**
     * 线下自营下单（V1.5 全自动：余额抵扣四重 min 由系统计算，用户/商家不手输）。
     *
     * @param couponId    可选：指定券；为空时系统自动选择对用户最有利的券
     * @param orderAmount 订单原始金额
     * @param drawBatchNo 可选：本次抽奖批次（支付成功后闭环）
     */
    @Transactional
    public OfflineOrder createOrder(String userPhone, String merchantNo, Long couponId,
                                    BigDecimal orderAmount, BigDecimal paidAmount, String drawBatchNo) {
        memberService.requireWriteAllowed(merchantNo);
        OrderCalcService.OrderCalc calc = orderCalcService.calc(userPhone, merchantNo, couponId, orderAmount);
        String bizNo = "OFF-" + orderNo();
        Long usedCouponId = calc.coupon() == null ? null : calc.coupon().getCouponId();
        BigDecimal deducted = calc.actualDeduct();
        OfflineOrder order = new OfflineOrder();
        order.setOfflineOrderNo(bizNo);
        order.setUserPhone(userPhone);
        order.setMerchantNo(merchantNo);
        order.setCouponId(usedCouponId);
        order.setDeductBalance(deducted);
        order.setOrderAmount(orderAmount);
        order.setPayAmount(calc.payAmount());
        // V1.5 细化：用户回填实际付金额（对账用，平台不碰资金）；P0 差异标识
        order.setPaidAmount(paidAmount);
        order.setPaidDiff(paidAmount == null ? null
                : paidAmount.subtract(calc.payAmount()).setScale(2, java.math.RoundingMode.HALF_UP));
        // 模式 A：下单即结算闭环（券核销/扣余额/额度/返还统一在 settleAssets）
        order.setOrderStatus(1);
        settleAssets(order, calc, deducted, usedCouponId, userPhone, merchantNo, bizNo);
        order.setRefundStatus(0);
        order.setRefundAmount(BigDecimal.ZERO);
        LocalDateTime now = LocalDateTime.now();
        order.setCreateTime(now);
        order.setPayTime(now);
        order.setUpdateTime(now);
        OfflineOrder saved = orderRepository.save(order);
        // 流程 C1：支付成功 → 本次抽奖产出全部资产闭环
        if (drawBatchNo != null && !drawBatchNo.isBlank()) {
            flowCloseService.closeByDrawBatch(drawBatchNo);
        }
        announceOrder(merchantNo, saved, "order_auto", false);
        return saved;
    }

    /** 退款：refundAmount 为 null 表示全额退款 */
    @Transactional
    public OfflineOrder refund(String offlineOrderNo, BigDecimal refundAmount) {
        OfflineOrder order = orderRepository.findById(offlineOrderNo)
                .orElseThrow(() -> new BizException("订单不存在"));
        memberService.requireWriteAllowed(order.getMerchantNo());
        if (order.getRefundStatus() != 0) {
            throw new BizException("订单已退款，不能重复退款");
        }
        boolean full = refundAmount == null;
        if (!full && (refundAmount.signum() <= 0 || refundAmount.compareTo(order.getPayAmount()) > 0)) {
            throw new BizException("退款金额不合法");
        }
        // 全额：券恢复；部分：券作废不恢复
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
            balanceService.refund(order.getUserPhone(), order.getMerchantNo(), refunded, offlineOrderNo,
                    full ? "线下订单全额退款" : "线下订单部分退款");
            orderCalcService.refundQuota(order.getUserPhone(), order.getMerchantNo(), refunded);
        }
        auditLogService.record("merchant", order.getMerchantNo(), "refund",
                order.getUserPhone(), refunded, "退款订单 " + offlineOrderNo + " 全额=" + full,
                AuditLogService.RT_REFUND_REVERSE, offlineOrderNo);
        // V1.5：退款同步扣回本单已发放的跨店返还余额（允许台账负余额）
        if (order.getReturnBalance() != null && order.getReturnBalance().signum() > 0) {
            balanceService.clawbackReturn(order.getUserPhone(), order.getMerchantNo(),
                    order.getReturnBalance(), offlineOrderNo);
        }
        order.setRefundStatus(full ? 1 : 2);
        order.setRefundAmount(refunded);
        order.setRefundTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        OfflineOrder savedRefund = orderRepository.save(order);
        announceService.record(order.getMerchantNo(), "refund",
                "本订单已办理退款，优惠已回滚，余额已退回，今日本店余额抵扣额度同步返还。");
        return savedRefund;
    }

    /** 订单完成播报（有/无抵扣话术） */
    private void announceOrder(String merchantNo, OfflineOrder order, String eventType, boolean manual) {
        try {
            String couponTxt = order.getCouponId() == null ? "无券"
                    : (couponService.get(order.getCouponId()).getPrizeType() == 1 ? "折扣" : "立减");
            String head = manual ? "商家人工处理订单" : "订单处理完成";
            String content;
            if (order.getDeductBalance() != null && order.getDeductBalance().signum() > 0) {
                content = head + "，订单原价" + order.getOrderAmount() + "元，盲盒优惠" + couponTxt
                        + "，按盲盒后金额自动抵扣余额" + order.getDeductBalance() + "元，顾客实付"
                        + order.getPayAmount() + "元，券已核销，本店今日余额抵扣剩余额度请查看后台。";
            } else {
                content = head + "，订单原价" + order.getOrderAmount() + "元，盲盒优惠" + couponTxt
                        + "，未抵扣余额，顾客实付" + order.getPayAmount() + "元，券已核销。";
            }
            announceService.record(merchantNo, eventType, content);
        } catch (Exception e) {
            // 播报失败不影响主流程
        }
    }

    /**
     * 结算资产（模式A下单时 / 模式B商家确认完成时）：
     * 扣余额 + 核销券 + 累加单日额度 + 跨店返还。
     */
    private void settleAssets(OfflineOrder order, OrderCalcService.OrderCalc calc, BigDecimal deducted,
                              Long usedCouponId, String userPhone, String merchantNo, String bizNo) {
        if (calc.coupon() != null) {
            try {
                couponService.verifyOffline(calc.coupon().getCouponId(), merchantNo, bizNo);
            } catch (com.ibigou.blindbox.common.BizException e) {
                throw new com.ibigou.blindbox.common.BizException("券核销失败(可能已被使用): " + e.getMessage());
            }
        }
        if (deducted.signum() > 0) {
            balanceService.deduct(userPhone, merchantNo, deducted, deducted,
                    VerifyType.OFFLINE_AUTO.getCode(), bizNo);
            orderCalcService.addQuota(userPhone, merchantNo, deducted);
        }
        BigDecimal returnBalance = grantCrossStoreReturn(userPhone, merchantNo, calc.payAmount(), bizNo);
        order.setReturnBalance(returnBalance == null ? BigDecimal.ZERO : returnBalance);
        order.setDeductBalance(deducted);
    }

    /** 本店订单列表（按手机号/日期筛选，用于对账与退款定位） */
    public java.util.List<OfflineOrder> listOrders(String merchantNo, String userPhone, String date) {
        java.util.List<OfflineOrder> all = orderRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo);
        return all.stream()
                .filter(o -> userPhone == null || userPhone.isBlank() || o.getUserPhone().equals(userPhone))
                .filter(o -> date == null || date.isBlank()
                        || (o.getCreateTime() != null && o.getCreateTime().toLocalDate().toString().equals(date)))
                .toList();
    }

    /** 录入本单微信/支付宝交易流水号 */
    @Transactional
    public OfflineOrder saveTradeNo(String orderNo, String merchantNo, String tradeNo) {
        OfflineOrder order = orderRepository.findById(orderNo)
                .filter(o -> o.getMerchantNo().equals(merchantNo))
                .orElseThrow(() -> new BizException("订单不存在或无权操作"));
        order.setMerchantTradeNo(tradeNo);
        order.setUpdateTime(LocalDateTime.now());
        return orderRepository.save(order);
    }

    // ================= V1.5 模式 B：一次性订单凭证 + 商家确认完成 =================

    /** 模式B下单：生成挂起订单 + 一次性凭证 token（不扣资产，等商家确认完成） */
    @Transactional
    public OfflineOrder createOrderB(String userPhone, String merchantNo, Long couponId,
                                     BigDecimal orderAmount) {
        memberService.requireWriteAllowed(merchantNo);
        OrderCalcService.OrderCalc calc = orderCalcService.calc(userPhone, merchantNo, couponId, orderAmount);
        String bizNo = "OFF-" + orderNo();
        OfflineOrder order = new OfflineOrder();
        order.setOfflineOrderNo(bizNo);
        order.setUserPhone(userPhone);
        order.setMerchantNo(merchantNo);
        order.setCouponId(calc.coupon() == null ? null : calc.coupon().getCouponId());
        order.setDeductBalance(BigDecimal.ZERO);
        order.setOrderAmount(orderAmount);
        order.setPayAmount(calc.payAmount());
        order.setReturnBalance(BigDecimal.ZERO);
        order.setOrderStatus(0); // 待确认
        order.setVoucherToken(UUID.randomUUID().toString().replace("-", "") + "V");
        LocalDateTime nowB = LocalDateTime.now();
        order.setVoucherExpire(nowB.plusMinutes(30));
        order.setRefundStatus(0);
        order.setRefundAmount(BigDecimal.ZERO);
        order.setCreateTime(nowB);
        order.setUpdateTime(nowB);
        return orderRepository.save(order);
    }

    /** 凭证核验（商家扫码/输入 token 查订单详情，仅核验不能收款） */
    public OfflineOrder voucherInfo(String token) {
        OfflineOrder order = orderRepository.findByVoucherToken(token)
                .orElseThrow(() -> new BizException("凭证无效或已失效"));
        if (order.getOrderStatus() != 0 || order.getVoucherExpire() == null
                || order.getVoucherExpire().isBefore(LocalDateTime.now())) {
            throw new BizException("订单凭证已失效（订单已确认或过期）");
        }
        return order;
    }

    /** 商家确认完成（模式B闭环）：扣余额/核销券/发放返还/累加额度；凭证作废 */
    @Transactional
    public OfflineOrder confirmOrderB(String orderNo, String operatorMerchantNo) {
        memberService.requireWriteAllowed(operatorMerchantNo);
        OfflineOrder order = orderRepository.findByIdForUpdate(orderNo)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (order.getOrderStatus() != 0) {
            throw new BizException("订单状态不可确认（非待确认状态）");
        }
        // 重算（以用户下单时的金额与券为准）
        OrderCalcService.OrderCalc calc = orderCalcService.calc(order.getUserPhone(),
                operatorMerchantNo, order.getCouponId(), order.getOrderAmount());
        String bizNo = order.getOfflineOrderNo();
        BigDecimal deducted = calc.actualDeduct();
        settleAssets(order, calc, deducted, order.getCouponId(), order.getUserPhone(),
                operatorMerchantNo, bizNo);
        order.setOrderStatus(1);
        order.setVoucherToken(null); // 一次性，确认后失效
        order.setVoucherExpire(null);
        order.setPayAmount(calc.payAmount());
        order.setUpdateTime(LocalDateTime.now());
        OfflineOrder savedB = orderRepository.save(order);
        announceOrder(operatorMerchantNo, savedB, "order_auto", false);
        return savedB;
    }

    /** 跨店返还：实付 × 平台配置比例（5%），发放到余额并落流水 */
    private BigDecimal grantCrossStoreReturn(String userPhone, String merchantNo,
                                             BigDecimal payAmount, String bizNo) {
        if (payAmount == null || payAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int percent = Integer.parseInt(globalConfigService.get("cross_store_return_percent", "5"));
        if (percent <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = payAmount.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.DOWN);
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        balanceService.grantAvailable(userPhone, merchantNo, amount,
                "跨店返还(实付" + payAmount + "×" + percent + "%)", bizNo);
        userMessageService.record(userPhone, "return_balance", "获得返还余额",
                "恭喜您，本次消费已获得返还余额 " + amount + " 元，已计入您的账户，请前往余额明细查看。");
        return amount;
    }

    private String orderNo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
