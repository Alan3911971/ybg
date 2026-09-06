package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 6.9 系统全局配置表（sys_global_config）
 * 平台管理员可编辑参数：
 *  - balance_deduct_rate：余额最大抵扣百分比 0-100（0=全平台关闭余额抵扣）
 *  - ibigou_channel_switch：宜必购渠道总开关 0 关闭 1 开启
 */
@Entity
@Table(name = "sys_global_config")
@Getter
@Setter
public class SysGlobalConfig {

    @Id
    @Column(length = 64)
    private String configKey;

    @Column(nullable = false, length = 255)
    private String configValue;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false)
    private LocalDateTime updateTime;

    /** 常量 key */
    public static final String KEY_BALANCE_DEDUCT_RATE = "balance_deduct_rate";
    public static final String KEY_IBIGOU_CHANNEL_SWITCH = "ibigou_channel_switch";
    public static final String KEY_CUSTOMER_SMS_LOGIN_REQUIRED = "customer_sms_login_required";
}
