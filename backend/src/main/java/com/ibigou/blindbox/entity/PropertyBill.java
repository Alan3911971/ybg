package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物业账单表（property_bill）
 */
@Entity
@Table(name = "property_bill")
@Getter
@Setter
public class PropertyBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long billId;

    @Column(nullable = false, length = 64, unique = true)
    private String billNo;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Integer billType;

    @Column(nullable = false, length = 32)
    private String billPeriod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(precision = 12, scale = 2)
    private BigDecimal deducted;

    @Column(precision = 12, scale = 2)
    private BigDecimal paid;

    @Column(nullable = false)
    private LocalDate dueDate;

    private Integer status;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
