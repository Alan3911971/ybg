package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业车辆通行记录（property_vehicle_log）
 */
@Entity
@Table(name = "property_vehicle_log")
@Getter
@Setter
public class PropertyVehicleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false, length = 64)
    private String deviceId;

    @Column(nullable = false, length = 20)
    private String plateNo;

    @Column(nullable = false)
    private Integer direction;

    @Column
    private LocalDateTime enterTime;

    @Column
    private LocalDateTime exitTime;

    @Column
    private Integer durationMin = 0;

    @Column(precision = 10, scale = 2)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column
    private Integer payStatus = 0;

    @Column(nullable = false)
    private Integer vehicleType;

    @Column(length = 255)
    private String snapshotUrl;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
