package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业访客车辆表（property_visitor_vehicle）
 */
@Entity
@Table(name = "property_visitor_vehicle")
@Getter
@Setter
public class PropertyVisitorVehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long visitId;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 20)
    private String plateNo;

    @Column(length = 64)
    private String visitorName;

    @Column(length = 20)
    private String visitorPhone;

    @Column(nullable = false)
    private Long communityId;

    @Column
    private Long roomId;

    @Column(nullable = false)
    private LocalDateTime arriveTime;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    @Column
    private Integer status = 1;

    @Column
    private LocalDateTime usedTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
