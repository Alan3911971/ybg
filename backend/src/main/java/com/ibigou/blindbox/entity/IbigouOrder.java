package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 6.10 宜必购内部订单表（ibigou_order）
 * 完全内部模块，biz_no = ibigou_order_no 贯穿对账报表。
 */
@Entity
@Table(name = "ibigou_order")
@Getter
@Setter
public class IbigouOrder {

    @Id
    @Column(length = 64)
    private String ibigouOrderNo;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 消费归属商家（对账用，本单消耗资产发行商家） */
    @Column(nullable = false, length = 64)
    private String merchantNo;

    /** null 未使用券 */
    @Column
    private Long couponId;

    /** 本单扣减余额金额 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductBalance;

    /** 订单原始金额 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal orderAmount;

    /** 实付金额 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal payAmount;

    /** 0 未退款 1 全额退款 2 部分退款 */
    @Column(nullable = false)
    private Integer refundStatus;

    /** 已经退款金额 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime refundTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
