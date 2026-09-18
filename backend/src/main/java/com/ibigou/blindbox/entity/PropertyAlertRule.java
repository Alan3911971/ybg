package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 告警规则表（property_alert_rule）
 */
@Entity
@Table(name = "property_alert_rule")
@Getter
@Setter
public class PropertyAlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String deviceType;

    @Column(nullable = false, length = 30)
    private String alertType;

    private Integer threshold = 1;

    private Integer windowMinutes = 5;

    private Integer silenceMinutes = 30;

    private Integer autoWorkorder = 1;

    private Integer notifyWechat = 0;

    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
