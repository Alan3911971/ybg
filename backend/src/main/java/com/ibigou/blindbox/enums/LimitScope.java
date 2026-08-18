package com.ibigou.blindbox.enums;

/**
 * 限额作用范围（limit_scope）
 * 1 平台全局，2 单商家，3 单用户
 */
public enum LimitScope {
    GLOBAL(1, "平台全局"),
    MERCHANT(2, "单商家"),
    USER(3, "单用户");

    private final int code;
    private final String desc;

    LimitScope(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static LimitScope of(int code) {
        for (LimitScope t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知限额范围: " + code);
    }
}
