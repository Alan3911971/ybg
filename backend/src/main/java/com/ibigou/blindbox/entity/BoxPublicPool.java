package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 6.2 公共共享奖品池（box_public_pool）
 * 档位为私有池投放的副本；投放商家不可抽自己投放的公共奖品。
 */
@Entity
@Table(name = "box_public_pool")
@Getter
@Setter
public class BoxPublicPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long publicId;

    @Column(name = "source_merchant_no", nullable = false, length = 64)
    private String sourceMerchantNo;

    @Column(nullable = false)
    private Integer prizeType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prizeValue;

    @Column(nullable = false)
    private Integer weight;

    @Column(nullable = false)
    private Integer enabled;

    @Column(length = 255)
    private String remark;

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
