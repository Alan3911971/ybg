package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.DailyDeductQuota;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.UserCoupon;
import com.ibigou.blindbox.enums.PrizeType;
import com.ibigou.blindbox.repository.DailyDeductQuotaRepository;
import com.ibigou.blindbox.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * V1.5 定稿订单自动计算服务（开发重点检查清单第 1/2 条）。
 * <p>顺序严格：先盲盒优惠（折扣/立减券）→ 再余额抵扣（基数为盲盒后金额）；
 * 余额抵扣四重条件取最小值，全自动，用户/商家不手输。</p>
 * <pre>
 * 盲盒后金额 = 折扣 ? 原价 × 折扣/10 : max(原价 − 立减, 0)
 * 免单(盲盒后金额=0)：实际抵扣=0，实付=0
 * 理论抵扣   = 盲盒后金额 × 门店抵扣百分比%
 * 实际抵扣   = min(①用户可用余额, ②理论抵扣,
 *                  ③单日上限−今日已累计, ④盲盒后金额)
 * 实付       = 盲盒后金额 − 实际抵扣
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class OrderCalcService {

    private final MerchantRepository merchantRepository;
    private final DailyDeductQuotaRepository quotaRepository;
    private final BalanceService balanceService;
    private final GlobalConfigService configService;
    private final CouponService couponService;

    /** 计算订单（couponId 为空时自动选择对用户最有利的券） */
    @Transactional
    public OrderCalc calc(String userPhone, String merchantNo, Long couponId, BigDecimal orderAmount, String rule, Boolean useBalance) {
        if (orderAmount == null || orderAmount.signum() <= 0) {
            throw new BizException("订单金额必须大于 0");
        }
        Merchant merchant = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));

        // 1) 自动选券：指定券 或 对用户最有利（盲盒后金额最小）；rule 非空时按开奖规则直接计算
        UserCoupon coupon = null;
        BigDecimal afterCoupon;
        if (rule != null && !rule.isBlank()) {
            afterCoupon = applyRule(rule, orderAmount);
        } else {
            if (couponId != null) {
                coupon = couponService.get(couponId);
            } else {
                List<UserCoupon> usable = couponService.availableForOffline(userPhone, merchantNo);
                if (!usable.isEmpty()) {
                    coupon = usable.stream()
                            .min(java.util.Comparator.comparing(c -> CouponService.afterCoupon(c, orderAmount)))
                            .orElse(null);
                }
            }
            afterCoupon = coupon == null ? orderAmount : CouponService.afterCoupon(coupon, orderAmount);
        }

        // 3) 免单边界：盲盒后金额=0 → 抵扣=0 实付=0
        if (afterCoupon.signum() <= 0) {
            return new OrderCalc(coupon, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    merchant.getReceiveQrImgWechat(), merchant.getReceiveQrImgAlipay(),
                    merchant.getReceiveQrImgUnionpay(), merchant.getReceiveQrImgOther(),
                    merchant.getReceiveMode(), merchant.getReceiveQrStatus());
        }

        // 4) 用户取消余额抵扣时，不抵扣任何余额（2026-09-10）
        if (useBalance != null && !useBalance) {
            return new OrderCalc(coupon, afterCoupon, BigDecimal.ZERO, BigDecimal.ZERO,
                    afterCoupon, BigDecimal.ZERO,
                    merchant.getReceiveQrImgWechat(), merchant.getReceiveQrImgAlipay(),
                    merchant.getReceiveQrImgUnionpay(), merchant.getReceiveQrImgOther(),
                    merchant.getReceiveMode(), merchant.getReceiveQrStatus());
        }

        // 5) 理论抵扣 = 盲盒后金额 × 门店百分比%（null 回退平台全局；0=本店禁止）
        int percent = merchant.getBalanceDeductPercent() == null
                ? configService.balanceDeductRate()
                : merchant.getBalanceDeductPercent();
        BigDecimal theoretical = percent <= 0
                ? BigDecimal.ZERO
                : afterCoupon.multiply(BigDecimal.valueOf(percent))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

        // 6) 四重 min
        BigDecimal available = balanceService.availableBalance(userPhone);
        BigDecimal quotaLimit = remainingDailyQuota(userPhone, merchantNo, merchant);
        BigDecimal actual = min4(available, theoretical, quotaLimit, afterCoupon);

        BigDecimal pay = afterCoupon.subtract(actual).max(BigDecimal.ZERO);
        return new OrderCalc(coupon, afterCoupon, theoretical, actual, pay, actual,
                merchant.getReceiveQrImgWechat(), merchant.getReceiveQrImgAlipay(),
                merchant.getReceiveQrImgUnionpay(), merchant.getReceiveQrImgOther(),
                merchant.getReceiveMode(), merchant.getReceiveQrStatus());
    }

    /** 按开奖规则计算盲盒后金额（rule JSON：free/discount/minus/threshold）；解析失败回退原价 */
    public static BigDecimal applyRule(String ruleJson, BigDecimal orderAmount) {
        if (ruleJson == null || ruleJson.isBlank()) {
            return orderAmount;
        }
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(ruleJson);
            String type = node.path("type").asText("");
            switch (type) {
                case "free":
                    return BigDecimal.ZERO;
                case "discount": {
                    double rate = node.path("rate").asDouble(1.0);
                    if (rate <= 0 || rate >= 1) {
                        return orderAmount;
                    }
                    return orderAmount.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP);
                }
                case "minus":
                    return orderAmount.subtract(node.path("amount").decimalValue()).max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.DOWN);
                case "threshold": {
                    BigDecimal threshold = node.path("threshold").decimalValue();
                    BigDecimal minus = node.path("minus").decimalValue();
                    if (orderAmount.compareTo(threshold) >= 0) {
                        return orderAmount.subtract(minus).max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
                    }
                    return orderAmount;
                }
                default:
                    return orderAmount;
            }
        } catch (Exception e) {
            return orderAmount;
        }
    }

    /** 今日剩余单日额度（无限制返回一个大数） */
    public BigDecimal remainingDailyQuota(String userPhone, String merchantNo, Merchant merchant) {
        BigDecimal limit = merchant.getDailyDeductLimit();
        if (limit == null || limit.signum() <= 0) {
            return new BigDecimal("999999999.99");
        }
        BigDecimal accum = quotaRepository
                .findForUpdate(userPhone, merchantNo, LocalDate.now())
                .map(DailyDeductQuota::getAccumDeduct)
                .orElse(BigDecimal.ZERO);
        return limit.subtract(accum).max(BigDecimal.ZERO);
    }

    /** 累加单日抵扣额度 */
    @Transactional
    public DailyDeductQuota addQuota(String userPhone, String merchantNo, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        LocalDate today = LocalDate.now();
        DailyDeductQuota q = quotaRepository
                .findForUpdate(userPhone, merchantNo, today)
                .orElseGet(() -> {
                    DailyDeductQuota n = new DailyDeductQuota();
                    n.setUserPhone(userPhone);
                    n.setMerchantNo(merchantNo);
                    n.setDeductDate(today);
                    n.setAccumDeduct(BigDecimal.ZERO);
                    n.setUpdateTime(LocalDateTime.now());
                    return n;
                });
        q.setAccumDeduct(q.getAccumDeduct().add(amount));
        q.setUpdateTime(LocalDateTime.now());
        return quotaRepository.save(q);
    }

    /** 退款返还：扣减单日累计（不低于 0） */
    @Transactional
    public DailyDeductQuota refundQuota(String userPhone, String merchantNo, BigDecimal amount) {
        DailyDeductQuota q = quotaRepository
                .findForUpdate(userPhone, merchantNo, LocalDate.now())
                .orElse(null);
        if (q == null) {
            return null;
        }
        BigDecimal after = q.getAccumDeduct().subtract(amount).max(BigDecimal.ZERO);
        q.setAccumDeduct(after);
        q.setUpdateTime(LocalDateTime.now());
        return quotaRepository.save(q);
    }

    private BigDecimal min4(BigDecimal a, BigDecimal b, BigDecimal c, BigDecimal d) {
        return a.min(b).min(c).min(d).max(BigDecimal.ZERO);
    }

    /** 计算明细（含模式A收款码，供 H5 展示） */
    public record OrderCalc(UserCoupon coupon, BigDecimal afterCoupon, BigDecimal theoreticalDeduct,
                            BigDecimal actualDeduct, BigDecimal payAmount, BigDecimal dailyQuotaUsed,
                            String receiveQrImgWechat, String receiveQrImgAlipay,
                            String receiveQrImgUnionpay, String receiveQrImgOther,
                            Integer receiveMode, Integer receiveQrStatus) {
    }
}
