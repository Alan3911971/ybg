package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P1：用户站内消息（user_message）。跨店返还/退款等触达用户。
 */
@Entity
@Table(name = "user_message")
@Getter
@Setter
public class UserMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** return_balance / refund / other */
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
