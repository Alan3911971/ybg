package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业主群消息 + 受理状态（property_group_msg）。
 * receiptStatus：0 无需回执 / 1 已受理 / 2 待人工判断 / 3 已忽略 / 4 已并单
 */
@Entity
@Table(name = "property_group_msg")
@Getter
@Setter
public class PropertyGroupMsg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long msgId;

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private Long companyId;

    private Long senderOwnerId;

    @Column(length = 64)
    private String senderName;

    @Column(length = 64)
    private String roomNo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** [{type,url}] */
    @Column(columnDefinition = "JSON")
    private String media;

    private LocalDateTime msgTime = LocalDateTime.now();

    private Integer isDemand = 0;

    /** repair / complaint / help / bill_ask / other */
    @Column(length = 32)
    private String demandKind;

    @Column(length = 64)
    private String intentLabel;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    private Long woId;

    @Column(length = 64)
    private String woNo;

    private Integer receiptStatus = 0;

    @Column(length = 500)
    private String receiptMsg;

    private LocalDateTime receiptTime;

    private Long handleAdminId;

    private LocalDateTime handleTime;

    @Column(length = 255)
    private String handleNote;

    private LocalDateTime createTime = LocalDateTime.now();
}
