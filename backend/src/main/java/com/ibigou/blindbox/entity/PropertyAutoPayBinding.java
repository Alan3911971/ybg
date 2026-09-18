package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业自动扣费绑定表（property_auto_pay_bindding）
 */
@Entity
@Table(name = "property_auto_pay_bindding")
@Getter
@Setter
public class PropertyAutoPayBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bindId;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 20)
    private String plateNo;

    @Column(nullable = false, length = 32)
    private String payChannel;

    @Column(length = 128)
    private String agreementNo;

    @Column
    private Integer status = 1;

    @Column
    private LocalDateTime signTime;

    @Column
    private LocalDateTime unsignTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
