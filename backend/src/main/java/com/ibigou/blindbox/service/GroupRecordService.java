package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.ThirdGroupVerifyRecord;
import com.ibigou.blindbox.repository.ThirdGroupVerifyRecordRepository;
import com.ibigou.blindbox.repository.UserCouponRepository;
import com.ibigou.blindbox.repository.UserBalanceFlowRepository;
import com.ibigou.blindbox.entity.UserCoupon;
import com.ibigou.blindbox.enums.PrizeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 第三方团购登记服务（流程 A/B/C2/C3）。
 * <p>仅做登记存档，不处理美团/饿了么真实核销；登记成功即触发本次抽奖批次闭环；
 * 团购订单禁止使用本次抽到的券/余额（接口层直接拦截相关参数）。</p>
 */
@Service
@RequiredArgsConstructor
public class GroupRecordService {

    private final ThirdGroupVerifyRecordRepository recordRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserBalanceFlowRepository userBalanceFlowRepository;
    private final CouponService couponService;
    private final BalanceService balanceService;
    private final UserMessageService userMessageService;
    private final GlobalConfigService globalConfigService;
    private final FlowCloseService flowCloseService;

    @Transactional
    public ThirdGroupVerifyRecord register(String qrCodeUniqueKey, String merchantNo, String userPhone,
                                           int channel, BigDecimal groupAmount,
                                           String drawBatchNo, String prizeInfo) {
        if (groupAmount == null || groupAmount.signum() <= 0) {
            throw new BizException("本次团购消费金额必须大于 0");
        }
        ThirdGroupVerifyRecord record = new ThirdGroupVerifyRecord();
        record.setQrCodeUniqueKey(qrCodeUniqueKey);
        record.setUserPhone(userPhone);
        record.setChannel(channel);
        record.setGroupAmount(groupAmount);
        record.setPrizeInfo(prizeInfo);
        record.setDrawBatchNo(drawBatchNo);
        record.setCreateTime(LocalDateTime.now());
        // YBG-C-RETURN-001：按盲盒奖品折算赠送余额（登记即发放，幂等防重）
        BigDecimal returnAmt = grantByPrize(record, merchantNo, drawBatchNo);
        record.setReturnBalance(returnAmt);
        ThirdGroupVerifyRecord saved = recordRepository.save(record);
        // 流程闭环②：登记记录保存成功
        if (drawBatchNo != null && !drawBatchNo.isBlank()) {
            flowCloseService.closeByDrawBatch(drawBatchNo);
        }
        return saved;
    }

    /**
     * YBG-C-RETURN-001：按本次抽奖奖品折算赠送余额。
     * 谢谢(无券无余额) → groupAmount×5%；折扣券N折 → groupAmount×N/10（原券核销）；
     * 立减券X元 → X 元余额（原券核销）；余额类奖品(3/4) → 开奖已发，不再赠送。
     * 幂等：同 drawBatchNo 已登记过则跳过。
     */
    private BigDecimal grantByPrize(ThirdGroupVerifyRecord record, String merchantNo, String drawBatchNo) {
        if (record.getGroupAmount() == null || record.getGroupAmount().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        // 幂等防重：同批次已登记（已有 return_desc）则跳过
        if (drawBatchNo != null && !drawBatchNo.isBlank()) {
            boolean exists = recordRepository.findAll().stream()
                    .anyMatch(r -> drawBatchNo.equals(r.getDrawBatchNo()) && r.getReturnDesc() != null);
            if (exists) {
                return BigDecimal.ZERO;
            }
        }
        // 有券：按券折算并核销原券
        if (drawBatchNo != null && !drawBatchNo.isBlank()) {
            java.util.List<UserCoupon> coupons = userCouponRepository.findByDrawBatchNo(drawBatchNo);
            for (UserCoupon coupon : coupons) {
                BigDecimal amt = BigDecimal.ZERO;
                String desc = null;
                int pt = coupon.getPrizeType();
                if (pt == PrizeType.DISCOUNT.getCode()) {
                    // 折扣券 N折 → 金额 × N/10
                    amt = record.getGroupAmount().multiply(coupon.getPrizeValue())
                            .divide(BigDecimal.valueOf(10), 2, java.math.RoundingMode.DOWN);
                    desc = "抽到" + coupon.getPrizeValue().stripTrailingZeros().toPlainString() + "折券，按" + coupon.getPrizeValue().stripTrailingZeros().toPlainString() + "折折算余额";
                } else if (pt == PrizeType.CUT.getCode()) {
                    // 立减券 X元 → 面值兑现
                    amt = coupon.getPrizeValue();
                    desc = "抽到立减券" + coupon.getPrizeValue().stripTrailingZeros().toPlainString() + "元，按面值折算余额";
                } else {
                    continue; // 其他类型券不折算
                }
                if (amt.signum() > 0) {
                    balanceService.grantAvailable(record.getUserPhone(), merchantNo, amt,
                            "团购渠道登记返还(" + desc + ")", drawBatchNo);
                    userMessageService.record(record.getUserPhone(), "return_balance", "获得返还余额",
                            "恭喜您，本次团购消费已获得返还余额 " + amt.stripTrailingZeros().toPlainString() + " 元（" + desc + "）");
                    // 折算后原券核销（防双重使用）
                    couponService.voidCoupon(coupon.getCouponId());
                    record.setReturnDesc(desc);
                    return amt;
                }
            }
        }
        // 余额类奖品(3/4)：开奖已发，不重复赠送
        if (drawBatchNo != null && !drawBatchNo.isBlank()) {
            boolean balanceGranted = userBalanceFlowRepository.findFirstByDrawBatchNoOrderByFlowIdAsc(drawBatchNo).isPresent();
            if (balanceGranted) {
                record.setReturnDesc("抽到余额奖品，开奖时已入账");
                return BigDecimal.ZERO;
            }
        }
        // 谢谢：groupAmount × cross_store_return_percent（默认 5%）
        int percent = Integer.parseInt(globalConfigService.get("cross_store_return_percent", "5"));
        if (percent > 0) {
            BigDecimal amt = record.getGroupAmount().multiply(BigDecimal.valueOf(percent))
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.DOWN);
            if (amt.signum() > 0) {
                balanceService.grantAvailable(record.getUserPhone(), merchantNo, amt,
                        "团购渠道登记返还(谢谢惠顾" + percent + "%)", drawBatchNo);
                userMessageService.record(record.getUserPhone(), "return_balance", "获得返还余额",
                        "恭喜您，本次团购消费已获得返还余额 " + amt.stripTrailingZeros().toPlainString() + " 元（谢谢惠顾，保底 " + percent + "%）");
                record.setReturnDesc("谢谢惠顾，按消费金额" + percent + "%赠送余额");
                return amt;
            }
        }
        return BigDecimal.ZERO;
    }
}
