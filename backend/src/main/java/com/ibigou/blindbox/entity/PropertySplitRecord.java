package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家→物业分账记录表（property_split_record）
 * 模型：消费者在商家消费 → 商家发起分账到关联物业公司 → 业主余额增加 → 抵扣物业费
 */
@Entity
@Table(name = "property_split_record")
@Getter
@Setter
public class PropertySplitRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long splitId;

    @Column(nullable = false, length = 64, unique = true)
    private String orderNo;

    @Column(nullable = false)
    private Long ownerId;

    /** 发起分账的商家编号（merchant.merchant_no） */
    @Column(nullable = false, length = 64)
    private String merchantNo;

    /** 接收分账的物业公司ID */
    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal orderAmount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal splitRatio;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal splitAmount;

    /** 分账模式 1=商家→物业 2=平台→物业 */
    @Column(nullable = false)
    private Integer splitMode = 1;

    /** 0待结算 1已结算 2已抵扣 3退款冲正 4失败 */
    private Integer splitStatus;

    private LocalDateTime settleTime;

    @Column(length = 128)
    private String wxTransactionId;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;

    /** 兼容 getMerchantId()：业务上 merchantNo 即商家ID */
    public String getMerchantId() { return merchantNo; }

    /** 兼容 setMerchantId(Long)：转存到 merchantNo */
    public void setMerchantId(Long merchantId) { this.merchantNo = merchantId == null ? null : String.valueOf(merchantId); }
}
