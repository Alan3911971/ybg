package com.ibigou.blindbox.enums;

/**
 * 第三方团购渠道（third_group_verify_record.channel）
 * 1 美团，2 饿了么，3 抖音
 */
public enum GroupChannel {
    MEITUAN(1, "美团"),
    ELEME(2, "饿了么"),
    DOUYIN(3, "抖音");

    private final int code;
    private final String desc;

    GroupChannel(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static GroupChannel of(int code) {
        for (GroupChannel t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知团购渠道: " + code);
    }
}
