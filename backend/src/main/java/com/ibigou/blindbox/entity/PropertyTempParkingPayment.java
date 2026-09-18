package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业临时停车缴费表（property_temp_parking_payment）
 */
@Entity
@Table(name = "property_temp_parking_payment")
@Getter
@Setter
public class PropertyTempParkingPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @Column(nullable = false, length = 64, unique = true)
    private String paymentNo;

    @Column(nullable = false)
    private Long vehicleLogId;

    @Column(nullable = false, length = 20)
    private String plateNo;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false)
    private Integer durationMin;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal feeAmount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount;

    @Column(nullable = false, length = 32)
    private String payChannel;

    @Column(length = 128)
    private String wxTransactionId;

    @Column(nullable = false)
    private LocalDateTime payTime;

    @Column(precision = 10, scale = 2)
    private BigDecimal splitAmount = BigDecimal.ZERO;

    @Column
    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
