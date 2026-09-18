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
