package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P2：系统告警（sys_alert）。未处理异常/定时任务失败记录，平台后台查看处理。
 */
@Entity
@Table(name = "sys_alert")
@Getter
@Setter
public class SysAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alertId;

    /** exception / job / payment / other */
    @Column(nullable = false, length = 32)
    private String alertType;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private Integer status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
