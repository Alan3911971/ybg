package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物业巡检记录表（property_inspection_record）
 */
@Entity
@Table(name = "property_inspection_record")
@Getter
@Setter
public class PropertyInspectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false)
    private Long inspectorId;

    @Column(nullable = false, length = 64)
    private String inspectorName;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private LocalDate checkDate;

    @Column(nullable = false)
    private LocalDateTime checkTime;

    @Column(precision = 10, scale = 6)
    private BigDecimal locationLat;

    @Column(precision = 10, scale = 6)
    private BigDecimal locationLng;

    @Column(length = 64)
    private String checkpointId;

    @Column(nullable = false)
    private Integer result;

    @Column(length = 500)
    private String issueDesc;

    @Column(columnDefinition = "JSON")
    private String issueImages;

    private Long woId;

    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
