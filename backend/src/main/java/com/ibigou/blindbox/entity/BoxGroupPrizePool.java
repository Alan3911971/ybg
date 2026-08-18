package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V1.4 补充表 B：美团/饿了么专属盲盒独立奖品池（box_group_prize_pool）。
 * 两套盲盒奖品池独立，不和本店普通盲盒、公共奖品池互通。
 */
@Entity
@Table(name = "box_group_prize_pool")
@Getter
@Setter
public class BoxGroupPrizePool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long groupPoolId;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    /** 1 美团 2 饿了么 */
    @Column(nullable = false)
    private Integer channel;

    @Column(nullable = false)
    private Integer prizeType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prizeValue;

    @Column(nullable = false)
    private Integer weight;

    @Column(nullable = false)
    private Integer enabled;

    @Column(nullable = false)
    private Integer isSupportIbigou;

    @Column(nullable = false)
    private Integer limitScope;

    @Column(nullable = false)
    private Integer limitCycle;

    @Column(nullable = false)
    private Integer limitMax;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
