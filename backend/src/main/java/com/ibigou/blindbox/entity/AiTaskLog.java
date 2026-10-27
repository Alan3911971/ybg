package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V19 AI 任务记录（ai_task_log）。
 * <p>审计与降级留痕：记录本次生成走的是大模型（llm）还是本地规则（rule）。</p>
 */
@Entity
@Table(name = "ai_task_log")
@Getter
@Setter
public class AiTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String merchantNo;

    /** visit_script / birthday_copy / churn_scan / appointment_reply */
    @Column(nullable = false, length = 32)
    private String bizType;

    @Column(length = 20)
    private String targetPhone;

    /** llm大模型 / rule本地规则 */
    @Column(nullable = false, length = 16)
    private String source;

    /** 模型名（rule 时为 local-rule） */
    @Column(length = 64)
    private String model;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(nullable = false)
    private Integer costMs;

    @Column(nullable = false)
    private LocalDateTime createTime;
}
