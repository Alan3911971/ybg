package com.ibigou.blindbox.enums;

/**
 * 宜必购订单退款状态（ibigou_order.refund_status）
 * 0 未退款，1 全额退款，2 部分退款
 */
public enum RefundStatus {
    NONE(0, "未退款"),
    FULL(1, "全额退款"),
    PARTIAL(2, "部分退款");

    private final int code;
    private final String desc;

    RefundStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static RefundStatus of(int code) {
        for (RefundStatus t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知退款状态: " + code);
    }
}
