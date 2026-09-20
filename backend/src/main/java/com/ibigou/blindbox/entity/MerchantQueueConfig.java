package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 商家排队配置表
 */
@Entity
@Table(name = "merchant_queue_config")
@Getter
@Setter
public class MerchantQueueConfig {

    @Id
    @Column(length = 64)
    private String merchantNo;

    /** 叫号前缀，如 A/B/C */
    @Column(length = 8)
    private String prefix = "A";

    /** 窗口数 */
    @Column(nullable = false)
    private Integer windowCount = 1;

    /** 预计等待分钟数 */
    @Column(nullable = false)
    private Integer estWaitMin = 10;

    /** 是否启用 0关 1开 */
    @Column(nullable = false)
    private Integer enabled = 1;

    /** 桌型配置 JSON：[{"seats":2,"tables":3},{"seats":4,"tables":5}]，空=不选桌型 */
    @Column(columnDefinition = "TEXT")
    private String seatConfig;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
