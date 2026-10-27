package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业动作留痕（property_action_log）。
 *
 * 设计稿「执行记录」屏的数据源：每条动作都记 谁发起 / 什么动作 / 影响面 / 授权档 / 能不能撤回。
 * 三级授权：L1 只读直答（本表只记不拦）、L2 出草稿等人确认、L3 只能你做（动钱动人权限）。
 * 日志不可删——本表只提供「撤回」（status=2），不提供删除接口。
 */
@Entity
@Table(name = "property_action_log")
@Getter
@Setter
public class PropertyActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    private Long companyId = 0L;

    private Long adminId;

    @Column(length = 64)
    private String adminName;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 128)
    private String actionLabel;

    @Column(length = 32)
    private String targetType;

    private Long targetId;

    @Column(length = 160)
    private String targetLabel;

    @Column(length = 255)
    private String scopeDesc;

    @Column(nullable = false, length = 8)
    private String authLevel = "L2";

    /** 0待执行 1已执行 2已撤回 3已驳回 */
    private Integer status = 1;

    @Column(columnDefinition = "TEXT")
    private String beforeJson;

    @Column(columnDefinition = "TEXT")
    private String afterJson;

    @Column(length = 500)
    private String note;

    private String batchNo;

    private LocalDateTime undoDeadline;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
