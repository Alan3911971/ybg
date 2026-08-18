package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 6.4 盲盒中奖统计表（box_prize_limit_stat）
 * 记录每一次中奖；退款不会回滚中奖计数。限额统计用该表做行锁/计数。
 */
@Entity
@Table(name = "box_prize_limit_stat")
@Getter
@Setter
public class BoxPrizeLimitStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long statId;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 关联私有池 id；公共奖品填 public_id */
    @Column(nullable = false)
    private Long prizeId;

    /** 1 私有池 2 公共池 3 美团专属池 4 饿了么专属池 */
    @Column(nullable = false)
    private Integer poolType;

    /** 奖品类型冗余，便于限额统计 */
    @Column(nullable = false)
    private Integer prizeType;
    /** 一天一次防刷键（merchant|user|date，唯一约束并发防双击） */
    private String dailyKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
