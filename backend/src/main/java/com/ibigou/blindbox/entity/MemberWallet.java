package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员按商家钱包余额（lc_member_wallet）
 * 主键：(member_id, store_id) 联合唯一
 */
@Entity
@Table(name = "lc_member_wallet",
    uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "store_id"}))
@Getter
@Setter
public class MemberWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会员手机号 */
    @Column(nullable = false, length = 20)
    private String memberId;

    /** 商家编号 */
    @Column(nullable = false, length = 64)
    private String storeId;

    /** 商家名称（冗余便于展示） */
    @Column(length = 128)
    private String storeName;

    /** 可用余额 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    /** 冻结金额（预留） */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    /** 累计发放金额 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalGranted = BigDecimal.ZERO;

    /** 累计消费金额 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalConsumed = BigDecimal.ZERO;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
