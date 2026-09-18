package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业工单操作日志表（property_wo_log）
 */
@Entity
@Table(name = "property_wo_log")
@Getter
@Setter
public class PropertyWoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(nullable = false)
    private Long woId;

    private Long operatorId;

    @Column(nullable = false, length = 64)
    private String operatorName;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(length = 500)
    private String remark;

    private Integer beforeStatus;

    private Integer afterStatus;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;
}
