package com.ibigou.blindbox.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业业主表（property_owner）
 */
@Entity
@Table(name = "property_owner")
@Getter
@Setter
public class PropertyOwner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ownerId;

    @Column(nullable = false, length = 64, unique = true)
    private String ownerNo;

    @Column(nullable = false, length = 64)
    private String ownerName;

    @Column(nullable = false, length = 20, unique = true)
    private String ownerPhone;

    @JsonIgnore
    @Column(nullable = false, length = 128)
    private String loginPwd;

    @Column(length = 255)
    private String avatarUrl;

    @Column(precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal carryOver = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal pendingSplit = BigDecimal.ZERO;

    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
