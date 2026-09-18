package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业小区表（property_community）
 */
@Entity
@Table(name = "property_community")
@Getter
@Setter
public class PropertyCommunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long communityId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 128)
    private String communityName;

    @Column(nullable = false, length = 255)
    private String communityAddr;

    @Column(length = 255)
    private String bindQrCode;

    private Integer buildingCount = 0;

    private Integer roomCount = 0;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
