package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V1.5 P2：商家站内消息（merchant_message）。
 * 会员到期提醒（7/3/1 天前）由定时任务生成；支持已读。
 */
@Entity
@Table(name = "merchant_message")
@Getter
@Setter
public class MerchantMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    /** expire_remind / order / other */
    @Column(nullable = false, length = 32)
    private String msgType;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 500)
    private String content;

    @Column(nullable = false)
    private Integer isRead;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
