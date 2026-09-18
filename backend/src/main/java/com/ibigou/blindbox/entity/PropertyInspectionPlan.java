package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业巡检计划表（property_inspection_plan）
 */
@Entity
@Table(name = "property_inspection_plan")
@Getter
@Setter
public class PropertyInspectionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long planId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 128)
    private String planName;

    @Column(columnDefinition = "TEXT")
    private String routeDesc;

    @Column(columnDefinition = "JSON")
    private String checkpointIds;

    @Column(nullable = false)
    private Integer frequency;

    @Column(length = 32)
    private String scheduleTime;

    @Column(columnDefinition = "JSON")
    private String assigneeIds;

    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
