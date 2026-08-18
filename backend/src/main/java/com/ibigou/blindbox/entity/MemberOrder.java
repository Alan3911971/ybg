package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V1.5 补充表 H：商家续费订单（member_order）。
 * 年费 = 月单价 × 12；赠送月数由活动开关控制；有效期按叠加规则计算。
 */
@Entity
@Table(name = "member_order")
@Getter
@Setter
public class MemberOrder {

    @Id
    @Column(length = 64)
    private String orderNo;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 购买时长（12 个月） */
    @Column(nullable = false)
    private Integer buyMonths;

    /** 活动赠送时长（0=不送） */
    @Column(nullable = false)
    private Integer giftMonths;

    /** 总增加时长 = 购买 + 赠送 */
    @Column(nullable = false)
    private Integer totalMonths;

    /** 0 待支付 1 已支付 2 已取消 */
    @Column(nullable = false)
    private Integer status;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;

    private LocalDateTime payTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
