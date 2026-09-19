package com.ibigou.blindbox.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业钱包：业主在物业公司维度的余额（中奖余额入账 / 缴费抵扣）。
 */
@Data
@Entity
@Table(name = "property_wallet")
public class PropertyWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long walletId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "balance")
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
