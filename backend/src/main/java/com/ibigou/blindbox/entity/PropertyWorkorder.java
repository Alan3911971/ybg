package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业工单表（property_workorder）
 */
@Entity
@Table(name = "property_workorder")
@Getter
@Setter
public class PropertyWorkorder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long woId;

    @Column(nullable = false, length = 64, unique = true)
    private String woNo;

    @Column(nullable = false)
    private Long ownerId;

    private Long memberId;

    @Column(nullable = false)
    private Long companyId;

    private Long roomId;

    @Column(nullable = false)
    private Integer woType;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "JSON")
    private String images;

    private Integer urgency = 1;

    @Column(length = 64)
    private String category;

    private Long assigneeId;

    @Column(length = 64)
    private String assigneeName;

    private Integer status = 0;

    private Integer rating;

    @Column(length = 500)
    private String ratingComment;

    private LocalDateTime completedTime;

    private LocalDateTime ratedTime;

    private Integer timeoutCount = 0;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
