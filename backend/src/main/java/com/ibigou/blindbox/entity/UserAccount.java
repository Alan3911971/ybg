package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 6.6 用户余额账户（user_account）
 * 余额为用户全局资产；抵扣上限由平台参数 balance_deduct_rate 统一控制。
 */
@Entity
@Table(name = "user_account")
@Getter
@Setter
public class UserAccount {

    @Id
    @Column(length = 20)
    private String userPhone;

    /** 账户总可用余额 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalBalance;

    /** 乐观锁版本号，并发扣减保护 */
    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
