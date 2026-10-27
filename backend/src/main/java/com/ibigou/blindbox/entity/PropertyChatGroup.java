package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 业主群（property_chat_group）。
 * 群内说话即提需求，AI 静默受理；过程不进群。
 */
@Entity
@Table(name = "property_chat_group")
@Getter
@Setter
public class PropertyChatGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long groupId;

    @Column(nullable = false)
    private Long companyId;

    private Long communityId;

    @Column(nullable = false, length = 128)
    private String groupName;

    /** 1=全小区业主群 2=楼栋群 3=物业内部群 */
    private Integer groupType = 1;

    private Integer memberCount = 0;

    /** 是否开启 AI 静默受理 */
    private Integer intakeEnabled = 1;

    private Integer status = 1;

    private LocalDateTime createTime = LocalDateTime.now();

    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
