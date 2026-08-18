package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * V1.5 补充表 G：本店单人单日余额抵扣额度（daily_deduct_quota）。
 * 按 (user_phone, merchant_no, deduct_date) 维度累计；每日 0 点按日期天然清零。
 */
@Entity
@Table(name = "daily_deduct_quota")
@Getter
@Setter
public class DailyDeductQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quotaId;

    @Column(nullable = false, length = 20)
    private String userPhone;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    @Column(nullable = false)
    private LocalDate deductDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal accumDeduct;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
