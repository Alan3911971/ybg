package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P0 安全：顾客登录会话（customer_session）。
 * 短信验证码登录后签发；敏感操作接口校验 token。
 */
@Entity
@Table(name = "customer_session")
@Getter
@Setter
public class CustomerSession {

    @Id
    @Column(length = 64)
    private String token;

    @Column(nullable = false, length = 20)
    private String userPhone;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
