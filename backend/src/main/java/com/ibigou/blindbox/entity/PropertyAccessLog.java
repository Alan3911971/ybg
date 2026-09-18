package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业通行记录（property_access_log）
 */
@Entity
@Table(name = "property_access_log")
@Getter
@Setter
public class PropertyAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(nullable = false)
    private Long communityId;

    @Column(nullable = false, length = 64)
    private String deviceId;

    @Column
    private Long credentialId;

    @Column(nullable = false)
    private Integer passType;

    @Column(length = 20)
    private String userPhone;

    @Column(nullable = false)
    private Integer direction;

    @Column(nullable = false)
    private LocalDateTime passTime;

    @Column(length = 255)
    private String snapshotUrl;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
