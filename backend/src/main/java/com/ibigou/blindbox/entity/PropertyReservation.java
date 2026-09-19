package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 场地预约记录表（property_reservation）
 */
@Entity
@Table(name = "property_reservation")
@Getter
@Setter
public class PropertyReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, unique = true)
    private String reservationNo;

    @Column(nullable = false)
    private Long facilityId;

    @Column(nullable = false)
    private Long ownerId;

    /** 预约来源：1=业主 2=商家 */
    @Column(nullable = false)
    private Integer reservedType = 1;

    /** 商家预约的商家编号（reservedType=2 时非空） */
    @Column(length = 32)
    private String merchantNo;

    /** 收费预约的支付单号（RS 前缀） */
    @Column(length = 64)
    private String paymentNo;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private LocalDate reserveDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    private BigDecimal amount = BigDecimal.ZERO;

    private Integer payStatus = 0;

    private Integer status = 0;

    @Column(length = 300)
    private String remark;

    @Column(length = 300)
    private String cancelReason;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
