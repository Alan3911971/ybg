package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V1.5 补充表 I：人工操作审计日志（audit_log）。
 * 记录商家兜底核销/兜底完成/退款、平台人工调整有效期等；平台可审计撤销。
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(length = 64)
    private String merchantNo;

    @Column(nullable = false, length = 64)
    private String operator;

    /** manual_verify / manual_complete / refund / adjust_expire 等 */
    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 20)
    private String userPhone;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String detail;

    /** V1.5：关联 ID（券 coupon_id / 订单号 / 商家号） */
    @Column(length = 64)
    private String refId;

    /** V1.5：撤销类型 COUPON_RESTORE / BALANCE_RETURN / REFUND_REVERSE / EXPIRE_DECREASE / NONE */
    @Column(length = 32)
    private String revokeType;

    /** V1.5：0 未撤销 1 已撤销 */
    @Column(nullable = false)
    private Integer revoked;

    private LocalDateTime revokeTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
