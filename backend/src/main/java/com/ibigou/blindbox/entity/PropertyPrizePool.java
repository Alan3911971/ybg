package com.ibigou.blindbox.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业开奖配置（复刻商家奖品池）：company 维度，支持美团/团购/抖音渠道分组。
 */
@Data
@Entity
@Table(name = "property_prize_pool")
public class PropertyPrizePool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long prizeId;

    @Column(name = "company_id")
    private Long companyId;

    /** 0普通商品 1折扣券 2立减券 3普通余额 4团购免单余额 */
    @Column(name = "prize_type")
    private Integer prizeType = 0;

    @Column(name = "prize_value")
    private BigDecimal prizeValue = BigDecimal.ZERO;

    @Column(name = "weight")
    private Integer weight = 1;

    @Column(name = "enabled")
    private Integer enabled = 1;

    @Column(name = "remark")
    private String remark;

    /** 0本店奖品池 1公共奖品池 */
    @Column(name = "pool_type")
    private Integer poolType = 0;

    /** 0通用 1美团 2团购 3抖音 */
    @Column(name = "channel")
    private Integer channel = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
