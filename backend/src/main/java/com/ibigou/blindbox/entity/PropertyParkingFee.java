package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物业停车费账单（property_parking_fee）
 */
@Entity
@Table(name = "property_parking_fee")
@Getter
@Setter
public class PropertyParkingFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long feeId;

    @Column(nullable = false, unique = true, length = 64)
    private String feeNo;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long vehicleId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Integer feeType;

    @Column(nullable = false, length = 32)
    private String period;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(precision = 12, scale = 2)
    private BigDecimal deducted = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal paid = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column
    private Integer status = 0;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
