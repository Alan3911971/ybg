package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业公司表（property_company）
 */
@Entity
@Table(name = "property_company")
@Getter
@Setter
public class PropertyCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long companyId;

    @Column(nullable = false, length = 128)
    private String companyName;

    @Column(nullable = false, length = 64)
    private String contactName;

    @Column(nullable = false, length = 20)
    private String contactPhone;

    @Column(length = 64)
    private String wxMchId;

    @Column(nullable = false)
    private Integer status = 1;

    /** 场地预约是否收费：0=免费 1=收费（物业后台配置） */
    @Column(nullable = false)
    private Integer reservationFeeEnabled = 0;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
