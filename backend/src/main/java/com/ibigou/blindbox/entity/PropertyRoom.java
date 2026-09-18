package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业房间表（property_room）
 */
@Entity
@Table(name = "property_room")
@Getter
@Setter
public class PropertyRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roomId;

    @Column(nullable = false)
    private Long buildingId;

    private Integer unitNo = 1;

    @Column(nullable = false)
    private Integer floorNo;

    @Column(nullable = false, length = 32)
    private String roomNo;

    @Column(precision = 10, scale = 2)
    private BigDecimal roomArea = BigDecimal.ZERO;

    private Integer roomType = 1;

    private Integer status = 0;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
