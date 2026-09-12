package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 6.3 商家门店表（merchant）
 */
@Entity
@Table(name = "merchant")
@Getter
@Setter
public class Merchant {

    @Id
    @Column(length = 64)
    private String merchantNo;

    @Column(nullable = false, length = 128)
    private String merchantName;

    @Column(nullable = false, length = 64)
    private String loginAccount;

    /** BCrypt 加密存储 */
    @Column(nullable = false, length = 128)
    private String loginPwd;

    @Column(nullable = false)
    private Integer status;

    @Column(nullable = false)
    private Integer boxDiscountTotalWeight;

    @Column(nullable = false)
    private Integer boxCouponTotalWeight;

    @Column(nullable = false)
    private Integer privatePoolWeight;

    @Column(nullable = false)
    private Integer publicPoolWeight;

    /** 商家会员过期时间；null=永久；页面只读不允许编辑 */
    private LocalDateTime memberExpireTime;

    /** V1.5：本店余额抵扣百分比 0-100；null=用平台全局rate，0=本店禁止余额抵扣 */
    private Integer balanceDeductPercent;

    /** V1.5：本店单人单日余额抵扣总上限；null/0=不限制 */
    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal dailyDeductLimit;

    /** V1.5：收款模式 1模式A(上传收款码) 2模式B(扫码枪核验) */
    private Integer receiveMode;

    /** V1.5：模式A营业收款码图片URL（兼容单图） */
    @Column(length = 255)
    private String receiveQrImg;

    /** V1.5：模式A微信收款码图片URL（商家后台上传） */
    @Column(length = 255)
    private String receiveQrImgWechat;

    /** V1.5：模式A支付宝收款码图片URL（商家后台上传） */
    @Column(length = 255)
    private String receiveQrImgAlipay;

    /** 银联/云闪付收款码图片URL（商家后台上传） */
    @Column(length = 255)
    private String receiveQrImgUnionpay;

    /** 其它支付收款码图片URL（商家后台上传） */
    @Column(length = 255)
    private String receiveQrImgOther;

    /** 指定播报设备的登录token（null=所有设备播报） */
    @Column(length = 128)
    private String broadcastToken;

    /** P0：收款码审核 0未上传 1待审核 2通过 3驳回 */
    @Column(nullable = false)
    private Integer receiveQrStatus;

    /** 客户支付模式 0=静态收款码(默认) 1=API支付(微信/支付宝) */
    @Column(nullable = false)
    private Integer payMode;

    /** 商家地址 */
    @Column(length = 255)
    private String address;

    /** 预约电话 */
    @Column(length = 32)
    private String reservePhone;

    /** 纬度 */
    @Column(precision = 10, scale = 6)
    private java.math.BigDecimal latitude;

    /** 经度 */
    @Column(precision = 10, scale = 6)
    private java.math.BigDecimal longitude;

    /** 行业分类 */
    @Column(length = 32)
    private String industry;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
