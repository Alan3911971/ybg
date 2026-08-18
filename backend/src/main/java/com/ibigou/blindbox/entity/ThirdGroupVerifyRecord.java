package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 6.8 第三方团购登记记录表（third_group_verify_record）
 * 我方仅做登记存档，不处理美团/饿了么真实核销。
 */
@Entity
@Table(name = "third_group_verify_record")
@Getter
@Setter
public class ThirdGroupVerifyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    /** 二维码唯一 key，识别渠道（美团/饿了么） */
    @Column(nullable = false, length = 128)
    private String qrCodeUniqueKey;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 1 美团 2 饿了么 */
    @Column(nullable = false)
    private Integer channel;

    /** 用户录入团购消费金额 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal groupAmount;
    /** YBG-C-RETURN-001：抽奖批次号（折算防重） */
    private String drawBatchNo;
    /** YBG-C-RETURN-001：登记折算赠送余额 */
    private BigDecimal returnBalance;
    /** YBG-C-RETURN-001：折算赠送说明 */
    private String returnDesc;

    /** 本次中奖奖品 json 摘要 */
    @Column(columnDefinition = "TEXT")
    private String prizeInfo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
