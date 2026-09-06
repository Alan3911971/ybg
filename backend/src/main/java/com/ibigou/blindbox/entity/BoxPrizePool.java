package com.ibigou.blindbox.entity;

import com.ibigou.blindbox.enums.LimitCycle;
import com.ibigou.blindbox.enums.LimitScope;
import com.ibigou.blindbox.enums.PrizeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 6.1 商家私有奖品池（box_prize_pool）
 */
@Entity
@Table(name = "box_prize_pool")
@Getter
@Setter
public class BoxPrizePool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long prizeId;

    @Column(nullable = false, length = 64)
    private String merchantNo;

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
    private Integer isPutPublic;

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

    /** 公共池奖品截止时间（null=不限），过期后自动下架 */
    private LocalDateTime expireTime;

    /** 业务辅助：是否折扣券（用于大类权重归组） */
    @Transient
    public boolean isDiscountType() {
        return PrizeType.of(prizeType) == PrizeType.DISCOUNT;
    }
}
