package com.ibigou.blindbox.enums;

/**
 * 限额周期（limit_cycle）
 * 1 每日，2 每周，3 不限周期
 */
public enum LimitCycle {
    DAILY(1, "每日"),
    WEEKLY(2, "每周"),
    NONE(3, "不限周期");

    private final int code;
    private final String desc;

    LimitCycle(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static LimitCycle of(int code) {
        for (LimitCycle t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知限额周期: " + code);
    }
}
