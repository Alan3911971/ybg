package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V1.5 补充表 F：美团/饿了么专属盲盒大类权重（box_group_pool_config）。
 * 每商家每渠道一行：折扣大类总权重 vs 立减&余额大类总权重；0/0 时回退按档位权重直抽。
 */
@Entity
@Table(name = "box_group_pool_config")
@Getter
@Setter
public class BoxGroupPoolConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    /** 1 美团 2 饿了么 */
    @Column(nullable = false)
    private Integer channel;

    @Column(nullable = false)
    private Integer discountTotalWeight;

    @Column(nullable = false)
    private Integer couponBalanceTotalWeight;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
