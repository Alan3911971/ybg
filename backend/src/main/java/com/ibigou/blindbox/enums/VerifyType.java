package com.ibigou.blindbox.enums;

/**
 * 核销 / 扣减来源（verify_type）
 * 0 未核销；1 线下自营自动核销；2 商家 H5 手工核销/扣减；3 宜必购渠道核销/扣减
 */
public enum VerifyType {
    NONE(0, "未核销"),
    OFFLINE_AUTO(1, "线下自动核销"),
    MANUAL(2, "商家H5手工核销"),
    IBIGOU(3, "宜必购渠道核销");

    private final int code;
    private final String desc;

    VerifyType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static VerifyType of(int code) {
        for (VerifyType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知核销来源: " + code);
    }
}
