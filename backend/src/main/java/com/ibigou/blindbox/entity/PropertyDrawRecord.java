package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物业抽盒记录（V26）。
 * 用于：剩余次数、本期第几位、券号、今日已抽、概率公开。
 */
@Data
@Entity
@Table(name = "property_draw_record")
public class PropertyDrawRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "prize_id")
    private Long prizeId;

    /** 0普通 1折扣 2立减 3余额 4团购免单 */
    @Column(name = "prize_type", nullable = false)
    private Integer prizeType = 0;

    @Column(name = "prize_value", precision = 10, scale = 2)
    private BigDecimal prizeValue = BigDecimal.ZERO;

    @Column(name = "coupon_no", length = 40)
    private String couponNo;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "wallet_before", precision = 10, scale = 2)
    private BigDecimal walletBefore;

    @Column(name = "wallet_after", precision = 10, scale = 2)
    private BigDecimal walletAfter;

    /** 本期第几位 */
    @Column(name = "seq")
    private Integer seq = 0;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(name = "create_time")
    private LocalDateTime createTime = LocalDateTime.now();
}
