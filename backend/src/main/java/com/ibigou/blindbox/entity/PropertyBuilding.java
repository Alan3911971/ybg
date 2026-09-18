package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业楼栋表（property_building）
 */
@Entity
@Table(name = "property_building")
@Getter
@Setter
public class PropertyBuilding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long buildingId;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false, length = 64)
    private String buildingName;

    private Integer unitCount = 1;

    private Integer floorCount = 0;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
