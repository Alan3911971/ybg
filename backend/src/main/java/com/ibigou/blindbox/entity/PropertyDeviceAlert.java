package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 设备告警记录表（property_device_alert）
 */
@Entity
@Table(name = "property_device_alert")
@Getter
@Setter
public class PropertyDeviceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 30)
    private String alertType;

    private Integer level = 1;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String rawData;

    private Long ruleId;

    private Long workorderId;

    private Integer ackStatus = 0;

    private Long ackBy;

    private LocalDateTime ackTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
