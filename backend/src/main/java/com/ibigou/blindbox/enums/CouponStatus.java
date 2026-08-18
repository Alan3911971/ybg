package com.ibigou.blindbox.enums;

/**
 * 优惠券状态（user_coupon.status）
 * 0 未使用，1 已核销，2 已过期，3 已作废
 */
public enum CouponStatus {
    UNUSED(0, "未使用"),
    VERIFIED(1, "已核销"),
    EXPIRED(2, "已过期"),
    VOIDED(3, "已作废");

    private final int code;
    private final String desc;

    CouponStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static CouponStatus of(int code) {
        for (CouponStatus t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知优惠券状态: " + code);
    }
}
