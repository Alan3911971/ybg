package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业活动审核表（property_activity_audit）
 */
@Entity
@Table(name = "property_activity_audit")
@Getter
@Setter
public class PropertyActivityAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    @Column(nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 128)
    private String activityName;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountLevel;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(length = 500)
    private String applyReason;

    private Integer auditStatus;

    private Long auditorId;

    @Column(length = 255)
    private String auditRemark;

    private LocalDateTime auditTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
