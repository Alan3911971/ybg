package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物业车辆管理（property_vehicle）
 */
@Entity
@Table(name = "property_vehicle")
@Getter
@Setter
public class PropertyVehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vehicleId;

    @Column(nullable = false)
    private Long ownerId;

    @Column
    private Long memberId;

    @Column(nullable = false, unique = true, length = 20)
    private String plateNo;

    @Column
    private Integer plateColor = 1;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Integer parkType;

    @Column(nullable = false)
    private LocalDate validStart;

    @Column(nullable = false)
    private LocalDate validEnd;

    @Column
    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
