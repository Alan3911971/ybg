package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商家自定义会员类型（merchant_member_type）。
 */
@Entity
@Table(name = "merchant_member_type")
@Getter
@Setter
public class MerchantMemberType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String merchantNo;

    @Column(nullable = false, length = 16)
    private String typeName;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
