package com.ibigou.blindbox.entity;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 商家-物业关联表：定义分账通道配置。
 * 支持双模式：
 *   splitMode=1 商家→物业：消费者在商家消费，商家分账到关联物业公司
 *   splitMode=2 平台→物业：消费者在宜必购平台消费，平台分账到关联物业公司
 * 一个商家可同时启用两种模式（分别创建两条记录）。
 */
@Entity
@Table(name = "merchant_property_binding")
@Getter
@Setter
public class MerchantPropertyBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商家编号（关联 merchant.merchant_no） */
    @Column(nullable = false, length = 64)
    private String merchantNo;

    /** 物业公司ID */
    @Column(nullable = false)
    private Long companyId;

    /** 分账模式 1=商家→物业 2=平台→物业 */
    @Column(nullable = false)
    private Integer splitMode = 1;

    /** 自定义分账比例(%)，覆盖商家全局splitRatio；null=用商家全局比例 */
    @Column(precision = 5, scale = 2)
    private java.math.BigDecimal customSplitRatio;

    /** 关联状态 0停用 1启用 */
    @Column(nullable = false)
    private Integer status = 1;

    /** 备注 */
    @Column(length = 255)
    private String remark;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
