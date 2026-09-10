package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.UserCoupon;
import com.ibigou.blindbox.enums.VerifyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 商家 H5 手工兜底操作（V1.5 定稿第 7 条：系统自动计算抵扣，商家仅确认）。
 * <p>F-1 手工核销券：仅发行商家可核销，外来公共券禁止；verify_type=2。
 * F-2 兜底完成订单：输入订单原价，系统按四重 min 自动计算抵扣并扣余额、核销券（verify_type=2）。</p>
 * <p>注意：人工操作全部留存审计日志（V1.5 待实现）；退款不会自动恢复券/退回余额，需平台人工处理。</p>
 */
@Service
@RequiredArgsConstructor
public class ManualService {

    private final CouponService couponService;
    private final BalanceService balanceService;
    private final OrderCalcService orderCalcService;
    private final MemberService memberService;
    private final AuditLogService auditLogService;
    private final AnnounceService announceService;

    /** F-1 手工核销优惠券 */
    @Transactional
    public UserCoupon manualVerifyCoupon(Long couponId, String operatorMerchantNo) {
        memberService.requireWriteAllowed(operatorMerchantNo);
        UserCoupon coupon = couponService.verifyManual(couponId, operatorMerchantNo);
        auditLogService.record(operatorMerchantNo, operatorMerchantNo, "manual_verify",
                coupon.getUserPhone(), coupon.getPrizeValue(), "手工核销券 #" + couponId,
                AuditLogService.RT_COUPON_RESTORE, String.valueOf(couponId));
        return coupon;
    }

    /** F-2 兜底完成订单：自动算抵扣 + 扣余额 + 核销券（verify_type=2） */
    @Transactional
    public void manualCompleteOrder(String userPhone, String operatorMerchantNo,
                                    BigDecimal orderAmount) {
        memberService.requireWriteAllowed(operatorMerchantNo);
        OrderCalcService.OrderCalc calc = orderCalcService.calc(userPhone, operatorMerchantNo, null, orderAmount, null, true);
        String bizNo = "MANUAL-" + operatorMerchantNo + "-" + System.currentTimeMillis();
        if (calc.coupon() != null) {
            couponService.verifyManual(calc.coupon().getCouponId(), operatorMerchantNo);
        }
        if (calc.actualDeduct().signum() > 0) {
            balanceService.deduct(userPhone, operatorMerchantNo, calc.actualDeduct(), calc.actualDeduct(),
                    VerifyType.MANUAL.getCode(), bizNo);
            orderCalcService.addQuota(userPhone, operatorMerchantNo, calc.actualDeduct());
        }
        auditLogService.record(operatorMerchantNo, operatorMerchantNo, "manual_complete",
                userPhone, calc.actualDeduct(), "兜底完成订单 原价" + orderAmount + " 实付" + calc.payAmount(),
                AuditLogService.RT_BALANCE_RETURN, null);
        announceService.record(operatorMerchantNo, "manual_complete",
                "商家人工处理订单，订单原价" + orderAmount + "元，自动抵扣余额" + calc.actualDeduct()
                        + "元，顾客实付" + calc.payAmount() + "元，券已核销。");
    }
}
