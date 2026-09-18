package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * IoT设备表（property_device）
 */
@Entity
@Table(name = "property_device")
@Getter
@Setter
public class PropertyDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false, length = 64, unique = true)
    private String deviceNo;

    @Column(nullable = false, length = 100)
    private String deviceName;

    @Column(nullable = false, length = 30)
    private String deviceType;

    @Column(length = 200)
    private String location;

    @Column(length = 50)
    private String brand;

    @Column(length = 45)
    private String ipAddress;

    private Integer status = 1;

    private LocalDateTime lastHeartbeat;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
