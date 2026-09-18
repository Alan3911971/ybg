package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业扣款日志表（property_deduct_log）
 */
@Entity
@Table(name = "property_deduct_log")
@Getter
@Setter
public class PropertyDeductLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long billId;

    private Long splitId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductAmount;

    @Column(nullable = false)
    private Integer sourceType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal beforeBalance;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal afterBalance;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
