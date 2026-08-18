package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 6.5 用户优惠券表（user_coupon）
 */
@Entity
@Table(name = "user_coupon")
@Getter
@Setter
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long couponId;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 发行商家编号：线下仅该商家可核销 */
    @Column(nullable = false, length = 64)
    private String sourceMerchantNo;

    @Column(nullable = false)
    private Integer prizeType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prizeValue;

    /** 0 暂不可用（刚抽出）；1 可用（流程闭环后） */
    @Column(nullable = false)
    private Integer canUseAfterDraw;

    /** 0 禁止宜必购；1 允许宜必购渠道 */
    @Column(nullable = false)
    private Integer isSupportIbigou;

    /** 0 未使用 1 已核销 2 已过期 3 已作废 */
    @Column(nullable = false)
    private Integer status;

    /** 0 未核销 1 线下自动 2 手工核销 3 宜必购渠道核销 */
    @Column(nullable = false)
    private Integer verifyType;

    /** 关联业务单号（线下订单 / 宜必购订单号） */
    @Column(length = 128)
    private String bizNo;

    /** V1.4 补充：抽奖批次号（流程闭环按批次置 can_use_after_draw=1） */
    @Column(length = 64)
    private String drawBatchNo;

    @Column(nullable = false)
    private LocalDateTime validStart;

    @Column(nullable = false)
    private LocalDateTime validEnd;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
