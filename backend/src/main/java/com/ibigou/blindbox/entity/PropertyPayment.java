package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业账单支付单表（property_payment）
 * 业主在线缴纳物业费时生成，支付回调确认后更新对应账单状态。
 */
@Entity
@Table(name = "property_payment")
@Getter
@Setter
public class PropertyPayment {

    @Id
    @Column(length = 64)
    private String paymentNo;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long billId;

    @Column(nullable = false, length = 64)
    private String billNo;

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 16)
    private String channel;

    /** 0=待支付 1=已支付 2=已关闭 */
    private Integer status;

    /** 0=物业费 1=车位费 */
    @Column(nullable = false)
    private Integer feeType = 0;

    private LocalDateTime payTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
