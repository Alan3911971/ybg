package com.ibigou.blindbox.enums;

/**
 * 余额流水类型（user_balance_flow.flow_type）
 * 1 发放，2 扣减，3 退款退回；amount：发放/退款正数，扣减负数
 */
public enum FlowType {
    GRANT(1, "发放"),
    DEDUCT(2, "扣减"),
    REFUND(3, "退款退回");

    private final int code;
    private final String desc;

    FlowType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static FlowType of(int code) {
        for (FlowType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知流水类型: " + code);
    }
}
