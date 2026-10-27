package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 主动规则配置（property_ai_rule）。companyId=0 表示全局默认规则。
 * authorizeLevel：1=L1 自动执行 / 2=L2 待确认 / 3=L3 只能你做
 */
@Entity
@Table(name = "property_ai_rule")
@Getter
@Setter
public class PropertyAiRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ruleId;

    @Column(nullable = false)
    private Long companyId = 0L;

    @Column(nullable = false, length = 48)
    private String eventCode;

    @Column(nullable = false, length = 64)
    private String eventName;

    @Column(length = 200)
    private String triggerDesc;

    @Column(length = 500)
    private String actionDesc;

    private Integer authorizeLevel = 1;

    @Column(length = 200)
    private String budgetDesc;

    private Integer weekCount = 0;

    private Integer status = 1;

    private Integer sortNo = 0;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
