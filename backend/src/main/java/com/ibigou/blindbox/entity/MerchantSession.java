package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V1.4 补充表 D：商家登录会话（merchant_session）。
 * DB 持久化，重启不失效；token 过期由鉴权与定时任务惰性清理。
 */
@Entity
@Table(name = "merchant_session")
@Getter
@Setter
public class MerchantSession {

    @Id
    @Column(length = 64)
    private String token;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
