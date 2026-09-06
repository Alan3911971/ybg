package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 在线聊天消息（chat_message）：顾客与商家双向沟通。
 * fromRole: customer / merchant；merchantNo 标诀归属商家。
 */
@Entity
@Table(name = "chat_message",
        indexes = {@Index(name = "idx_chat_phone_mno", columnList = "userPhone,merchantNo"),
                   @Index(name = "idx_chat_mno_role", columnList = "merchantNo,fromRole")})
@Getter
@Setter
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 顾客手机号（双方会话主键） */
    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 归属商家编号 */
    @Column(nullable = false, length = 32)
    private String merchantNo;

    /** customer / merchant */
    @Column(nullable = false, length = 16)
    private String fromRole;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 0 未读 1 已读 */
    @Column(nullable = false)
    private Integer isRead;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (isRead == null) isRead = 0;
    }
}
