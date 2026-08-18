package com.ibigou.blindbox.enums;

/**
 * 奖品类型（prize_type）
 * 1 折扣券，2 立减券，3 普通余额，4 团购免单余额
 */
public enum PrizeType {
    DISCOUNT(1, "折扣券"),
    CUT(2, "立减券"),
    BALANCE(3, "普通余额"),
    GROUP_FREE(4, "团购免单余额"),
    THANKS(5, "谢谢参与");

    private final int code;
    private final String desc;

    PrizeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static PrizeType of(int code) {
        for (PrizeType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知奖品类型: " + code);
    }
}
