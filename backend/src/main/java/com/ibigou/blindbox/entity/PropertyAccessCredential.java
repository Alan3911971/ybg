package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业通行凭证（property_access_credential）
 */
@Entity
@Table(name = "property_access_credential")
@Getter
@Setter
public class PropertyAccessCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long credentialId;

    @Column(nullable = false)
    private Long ownerId;

    @Column
    private Long memberId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Integer credType;

    @Column(nullable = false, length = 255)
    private String credValue;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    @Column
    private Integer maxUseCount = 1;

    @Column
    private Integer usedCount = 0;

    @Column
    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
