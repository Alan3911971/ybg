package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 6.7 余额流水表（user_balance_flow）
 * 禁止物理删除（DB 触发器硬保护 + 应用层不提供删除接口），所有业务变更必须落流水。
 */
@Entity
@Table(name = "user_balance_flow")
@Getter
@Setter
public class UserBalanceFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long flowId;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 1 发放 2 扣减 3 退款退回 */
    @Column(nullable = false)
    private Integer flowType;

    /** 发放/退款正数；扣减存负数 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 0 暂不可用 1 可用 */
    @Column(nullable = false)
    private Integer canUseAfterDraw;

    /** 1 私有池 2 公共池 3 美团专属 4 饿了么专属 */
    @Column(nullable = false)
    private Integer sourcePoolType;

    /** 0 未消耗 1 线下自动 2 手工扣减 3 宜必购渠道扣减 */
    @Column(nullable = false)
    private Integer verifyType;

    /** 抽奖门店/消费门店（报表对账用，V1.4 补充） */
    @Column(length = 64)
    private String merchantNo;

    /** 业务单号（线下订单 / 宜必购订单号） */
    @Column(length = 128)
    private String bizNo;

    /** V1.4 补充：抽奖批次号（流程闭环按批次置 can_use_after_draw=1） */
    @Column(length = 64)
    private String drawBatchNo;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
