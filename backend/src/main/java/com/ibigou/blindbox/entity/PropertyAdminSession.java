package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业管理员登录会话（property_admin_session）
 */
@Entity
@Table(name = "property_admin_session")
@Getter
@Setter
public class PropertyAdminSession {

    @Id
    @Column(length = 64)
    private String token;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
