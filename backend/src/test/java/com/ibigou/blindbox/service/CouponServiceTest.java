package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.UserCoupon;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心计算单测：券后金额（折扣率 vs 立减）。
 */
class CouponServiceTest {

    private UserCoupon coupon(int prizeType, String value) {
        UserCoupon c = new UserCoupon();
        c.setPrizeType(prizeType);
        c.setPrizeValue(new BigDecimal(value));
        return c;
    }

    @Test
    void 折扣券按折扣率() {
        // 8 折：100 * 8/10 = 80
        assertEquals(new BigDecimal("80.00"),
                CouponService.afterCoupon(coupon(1, "8.00"), new BigDecimal("100.00")));
        // 7 折：50 * 7/10 = 35
        assertEquals(new BigDecimal("35.00"),
                CouponService.afterCoupon(coupon(1, "7.00"), new BigDecimal("50.00")));
    }

    @Test
    void 立减券按金额减() {
        // 立减 20：100 - 20 = 80
        assertEquals(new BigDecimal("80.00"),
                CouponService.afterCoupon(coupon(2, "20.00"), new BigDecimal("100.00")));
        // 立减超原价：不出现负数
        assertTrue(CouponService.afterCoupon(coupon(2, "120.00"), new BigDecimal("100.00"))
                .compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void 免单边界() {
        // 立减等于原价：免单 0
        assertTrue(CouponService.afterCoupon(coupon(2, "100.00"), new BigDecimal("100.00"))
                .compareTo(BigDecimal.ZERO) == 0);
    }
}
