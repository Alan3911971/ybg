package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业绑定关系表（property_bind_relation）
 */
@Entity
@Table(name = "property_bind_relation")
@Getter
@Setter
public class PropertyBindRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bindId;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long companyId;

    private Long roomId;

    @Column(nullable = false)
    private LocalDateTime bindTime;

    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
