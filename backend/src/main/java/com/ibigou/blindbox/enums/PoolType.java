package com.ibigou.blindbox.enums;

/**
 * 奖品池类型（pool_type / source_pool_type）
 * 1 私有池，2 公共池，3 美团专属池，4 饿了么专属池
 */
public enum PoolType {
    PRIVATE(1, "私有池"),
    PUBLIC(2, "公共池"),
    MEITUAN(3, "美团专属池"),
    ELEME(4, "饿了么专属池"),
    DOUYIN(5, "抖音专属池");

    private final int code;
    private final String desc;

    PoolType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static PoolType of(int code) {
        for (PoolType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知奖品池类型: " + code);
    }
}
