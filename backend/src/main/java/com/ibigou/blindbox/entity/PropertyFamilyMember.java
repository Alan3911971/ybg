package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业家庭成员表（property_family_member）
 */
@Entity
@Table(name = "property_family_member")
@Getter
@Setter
public class PropertyFamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 64)
    private String memberName;

    @Column(length = 20)
    private String memberPhone;

    @Column(nullable = false, length = 32)
    private String relation;

    private Integer status;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
