package com.ibigou.blindbox.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业管理员表（property_admin）
 */
@Entity
@Table(name = "property_admin")
@Getter
@Setter
public class PropertyAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminId;

    @Column(nullable = false, length = 64, unique = true)
    private String account;

    @JsonIgnore
    @Column(nullable = false, length = 128)
    private String loginPwd;

    @Column(nullable = false, length = 64)
    private String adminName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    private Long companyId;

    private Integer roleType;

    private Integer status;

    private LocalDateTime lastLoginTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
