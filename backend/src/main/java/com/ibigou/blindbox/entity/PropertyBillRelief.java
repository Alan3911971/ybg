package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单户账单减免申请（property_bill_relief）。
 *
 * 设计稿铁律：减免属于 L3「只能你做」，AI 只出证据包、不改金额。
 *   - 申请金额 ≤ 角色单笔额度 → 进待批（status=0），需第二人审批（审批人≠申请人）
 *   - 申请金额 > 角色额度     → status=3「超限只出证据包」，落库留痕但不改任何账目
 */
@Entity
@Table(name = "property_bill_relief")
@Getter
@Setter
public class PropertyBillRelief {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reliefId;

    private Long companyId = 0L;

    @Column(nullable = false)
    private Long billId;

    @Column(nullable = false)
    private Long ownerId;

    private Long roomId;

    /** 1滞纳金 2本金 3整单 */
    private Integer reliefType = 1;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal applyAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quotaAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String authLevel = "L3";

    /** 0待批 1已批 2已驳 3超限只出证据包 */
    private Integer status = 0;

    private Long applicantId;

    @Column(length = 64)
    private String applicantName;

    private Long approverId;

    @Column(length = 64)
    private String approverName;

    @Column(length = 500)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(length = 500)
    private String approveNote;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
