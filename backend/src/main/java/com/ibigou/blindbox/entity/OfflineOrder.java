package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V1.4 补充表 A：线下门店自营订单（offline_order），支撑流程 E。
 * biz_no 贯穿 user_coupon / user_balance_flow。
 */
@Entity
@Table(name = "offline_order")
@Getter
@Setter
public class OfflineOrder {

    @Id
    @Column(length = 64)
    private String offlineOrderNo;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 消费商家编号（核销券须为发行商家） */
    @Column(nullable = false, length = 64)
    private String merchantNo;

    /** null 未使用券 */
    @Column
    private Long couponId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductBalance;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal orderAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal payAmount;

    /** V1.5：用户实际付金额（H5 回填，对账用） */
    @Column(precision = 12, scale = 2)
    private BigDecimal paidAmount;

    /** V1.5：本单跨店返还余额（实付 × 比例） */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal returnBalance;

    /** V1.5 模式B：0 待确认(挂起) 1 已完成 2 已取消 3 已退款 */
    @Column(nullable = false)
    private Integer orderStatus;

    /** V1.5 模式B：一次性订单凭证 token（确认完成后失效） */
    @Column(length = 64)
    private String voucherToken;

    /** V1.5 模式B：凭证过期时间 */
    private LocalDateTime voucherExpire;

    /** V1.5 P2：商家微信/支付宝交易流水号（退款时去商户后台办理） */
    @Column(length = 64)
    private String merchantTradeNo;

    /** P0：实付差异（实际付 - 应付；非 0 为异常单，对账标识） */
    @Column(precision = 12, scale = 2)
    private BigDecimal paidDiff;

    /** 0 未退款 1 全额退款 2 部分退款 */
    @Column(nullable = false)
    private Integer refundStatus;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    private LocalDateTime payTime;

    /** 支付渠道 wechat/alipay */
    @Column(length = 32)
    private String payChannel;

    private LocalDateTime refundTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
