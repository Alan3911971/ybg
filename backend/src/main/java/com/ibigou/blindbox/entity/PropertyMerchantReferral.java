package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家邀请码与两段式佣金（property_merchant_referral）。
 * status：0 已出码 / 1 已扫码 / 2 已过审 / 3 已过期 / 4 已作废
 * 敞口只 ¥30（一人一码·单类目·72h）；¥70 绑首单满 ¥100 且 7 天无退款。
 */
@Entity
@Table(name = "property_merchant_referral")
@Getter
@Setter
public class PropertyMerchantReferral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long referralId;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 40, unique = true)
    private String code;

    @Column(nullable = false)
    private Long ownerId;

    @Column(length = 64)
    private String roomNo;

    @Column(length = 32)
    private String category;

    private Integer status = 0;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    @Column(length = 64)
    private String merchantNo;

    @Column(length = 128)
    private String merchantName;

    private Long bindingId;

    @Column(length = 500)
    private String licenseUrl;

    private LocalDateTime scanTime;

    private LocalDateTime auditTime;

    private Long auditAdminId;

    @Column(length = 255)
    private String auditNote;

    @Column(name = "reward30_status")
    private Integer reward30Status = 0;

    @Column(name = "reward30_time")
    private LocalDateTime reward30Time;

    @Column(name = "reward70_status")
    private Integer reward70Status = 0;

    @Column(name = "reward70_time")
    private LocalDateTime reward70Time;

    @Column(precision = 10, scale = 2)
    private BigDecimal firstOrderAmount;

    private LocalDateTime firstOrderTime;

    private LocalDateTime refundDeadline;

    private LocalDateTime createTime = LocalDateTime.now();

    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
