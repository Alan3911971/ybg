package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业服务订单表（property_vs_order）
 */
@Entity
@Table(name = "property_vs_order")
@Getter
@Setter
public class PropertyVsOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(nullable = false, length = 64, unique = true)
    private String orderNo;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long serviceId;

    @Column
    private Long merchantId;

    @Column
    private Long roomId;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal deductAmount = BigDecimal.ZERO;

    @Column(length = 32)
    private String payChannel;

    @Column
    private LocalDateTime appointmentTime;

    @Column
    private Integer status = 0;

    @Column(length = 128)
    private String wxTransactionId;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
