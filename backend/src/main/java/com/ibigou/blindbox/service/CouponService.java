package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.UserCoupon;
import com.ibigou.blindbox.enums.CouponStatus;
import com.ibigou.blindbox.enums.VerifyType;
import com.ibigou.blindbox.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券生命周期：发放（can_use_after_draw=0）/ 核销 / 恢复 / 作废。
 * 线下核销必须 source_merchant_no 匹配；宜必购渠道看 is_support_ibigou。
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    /** 券默认有效期（天），平台配置 coupon_valid_days（P1 可配） */
    public static final long COUPON_VALID_DAYS = 30;

    private final GlobalConfigService configService;

    private final UserCouponRepository couponRepository;

    /** 抽奖发放新券：can_use_after_draw=0（流程闭环前不可用） */
    @org.springframework.transaction.annotation.Transactional
    public UserCoupon grant(String userPhone, String sourceMerchantNo, int prizeType,
                            BigDecimal prizeValue, int isSupportIbigou, String drawBatchNo) {
        UserCoupon c = new UserCoupon();
        c.setUserPhone(userPhone);
        c.setSourceMerchantNo(sourceMerchantNo);
        c.setPrizeType(prizeType);
        c.setPrizeValue(prizeValue);
        c.setCanUseAfterDraw(0);
        c.setIsSupportIbigou(isSupportIbigou);
        c.setStatus(CouponStatus.UNUSED.getCode());
        c.setVerifyType(VerifyType.NONE.getCode());
        c.setDrawBatchNo(drawBatchNo);
        LocalDateTime now = LocalDateTime.now();
        c.setValidStart(now);
        long days;
        try {
            days = Long.parseLong(configService.get("coupon_valid_days", "30"));
        } catch (Exception e) {
            days = COUPON_VALID_DAYS;
        }
        c.setValidEnd(now.plusDays(days));
        c.setCreateTime(now);
        c.setUpdateTime(now);
        return couponRepository.save(c);
    }

    /** 线下自动核销：校验发行商家匹配、未核销、未过期、可用 */
    @org.springframework.transaction.annotation.Transactional
    public UserCoupon verifyOffline(Long couponId, String merchantNo, String bizNo) {
        UserCoupon c = getForVerify(couponId);
        if (!c.getSourceMerchantNo().equals(merchantNo)) {
            throw new BizException("无权核销：该券为其他商家发行");
        }
        c.setStatus(CouponStatus.VERIFIED.getCode());
        c.setVerifyType(VerifyType.OFFLINE_AUTO.getCode());
        c.setBizNo(bizNo);
        c.setUpdateTime(LocalDateTime.now());
        return couponRepository.save(c);
    }

    /** 宜必购渠道核销：不限制发行商家，看 is_support_ibigou */
    @org.springframework.transaction.annotation.Transactional
    public UserCoupon verifyIbigou(Long couponId, String bizNo) {
        UserCoupon c = getForVerify(couponId);
        if (c.getIsSupportIbigou() == null || c.getIsSupportIbigou() != 1) {
            throw new BizException("该券不支持宜必购渠道使用");
        }
        c.setStatus(CouponStatus.VERIFIED.getCode());
        c.setVerifyType(VerifyType.IBIGOU.getCode());
        c.setBizNo(bizNo);
        c.setUpdateTime(LocalDateTime.now());
        return couponRepository.save(c);
    }

    /** 商家 H5 手工核销（异常兜底）：仅发行商家可核销，外来公共券禁止 */
    @org.springframework.transaction.annotation.Transactional
    public UserCoupon verifyManual(Long couponId, String operatorMerchantNo) {
        UserCoupon c = getForVerify(couponId);
        if (!c.getSourceMerchantNo().equals(operatorMerchantNo)) {
            throw new BizException("无权操作：此券为其他商家发行，本店仅可查看");
        }
        c.setStatus(CouponStatus.VERIFIED.getCode());
        c.setVerifyType(VerifyType.MANUAL.getCode());
        c.setUpdateTime(LocalDateTime.now());
        return couponRepository.save(c);
    }

    /** 退款全额：恢复未核销状态（verify_type 清 0，biz_no 保留原单便于追溯） */
    @org.springframework.transaction.annotation.Transactional
    public UserCoupon restore(Long couponId) {
        UserCoupon c = couponRepository.findById(couponId)
                .orElseThrow(() -> new BizException("优惠券不存在"));
        c.setStatus(CouponStatus.UNUSED.getCode());
        c.setVerifyType(VerifyType.NONE.getCode());
        c.setUpdateTime(LocalDateTime.now());
        return couponRepository.save(c);
    }

    /** 退款部分：优惠券作废不恢复 */
    @org.springframework.transaction.annotation.Transactional
    public UserCoupon voidCoupon(Long couponId) {
        UserCoupon c = couponRepository.findById(couponId)
                .orElseThrow(() -> new BizException("优惠券不存在"));
        c.setStatus(CouponStatus.VOIDED.getCode());
        c.setUpdateTime(LocalDateTime.now());
        return couponRepository.save(c);
    }

    /** 线下可用券：可用 + 未使用 + 未过期 + 发行商家匹配 */
    public List<UserCoupon> availableForOffline(String userPhone, String merchantNo) {
        return couponRepository.findAllByUserPhoneAndStatusAndCanUseAfterDraw(userPhone,
                        CouponStatus.UNUSED.getCode(), 1).stream()
                .filter(c -> c.getSourceMerchantNo().equals(merchantNo))
                .filter(this::notExpired)
                .toList();
    }

    /** 宜必购可用券：可用 + 未使用 + 未过期 + 支持宜必购 */
    public List<UserCoupon> availableForIbigou(String userPhone) {
        return couponRepository.findAllByUserPhoneAndStatusAndCanUseAfterDraw(userPhone,
                        CouponStatus.UNUSED.getCode(), 1).stream()
                .filter(c -> c.getIsSupportIbigou() != null && c.getIsSupportIbigou() == 1)
                .filter(this::notExpired)
                .toList();
    }

    public UserCoupon get(Long couponId) {
        return couponRepository.findById(couponId).orElseThrow(() -> new BizException("优惠券不存在"));
    }

    private UserCoupon getForVerify(Long couponId) {
        UserCoupon c = get(couponId);
        if (c.getStatus() != CouponStatus.UNUSED.getCode()) {
            throw new BizException("优惠券状态不可核销（已核销/已过期/已作废）");
        }
        if (c.getCanUseAfterDraw() != 1) {
            throw new BizException("优惠券暂不可用，完成本次流程之后可消费");
        }
        if (!notExpired(c)) {
            throw new BizException("优惠券已过期");
        }
        return c;
    }

    private boolean notExpired(UserCoupon c) {
        return c.getValidEnd() != null && c.getValidEnd().isAfter(LocalDateTime.now());
    }

    /**
     * 券后金额（V1.5 决策）：
     * 折扣券(1) prize_value = 折扣率（8 = 8折），实付 = 金额 × 折扣率/10；
     * 立减券(2) prize_value = 立减金额，实付 = 金额 - 立减。
     */
    public static BigDecimal afterCoupon(UserCoupon c, BigDecimal orderAmount) {
        if (c.getPrizeType() == 1) {
            return orderAmount.multiply(c.getPrizeValue())
                    .divide(BigDecimal.TEN, 2, java.math.RoundingMode.HALF_UP);
        }
        return orderAmount.subtract(c.getPrizeValue()).max(BigDecimal.ZERO);
    }
}
