package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 业主群成员（property_chat_group_member）。房号冗余，用于群内报修自动定位。
 */
@Entity
@Table(name = "property_chat_group_member")
@Getter
@Setter
public class PropertyChatGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long groupId;

    /** 冗余公司ID：便于按物业公司统计群成员总数 */
    private Long companyId;

    private Long ownerId;

    private Long roomId;

    @Column(length = 64)
    private String roomNo;

    @Column(length = 64)
    private String memberName;

    @Column(length = 20)
    private String memberPhone;

    /** 1=业主 2=家属 3=物业员工 4=AI机器人 */
    private Integer memberRole = 1;

    private Integer status = 1;

    private LocalDateTime joinTime = LocalDateTime.now();
}
