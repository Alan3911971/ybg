package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 邻里互助挂牌（property_mutual_post）。
 * postType：1 车位出租 / 2 车位求租 / 3 找师傅 / 4 二手 / 5 拼车 / 6 照看
 * status：0 在挂 / 1 已成交 / 2 已下架 / 3 已过期
 */
@Entity
@Table(name = "property_mutual_post")
@Getter
@Setter
public class PropertyMutualPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    @Column(nullable = false)
    private Long companyId;

    private Long communityId;

    @Column(nullable = false)
    private Integer postType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Long ownerId;

    @Column(length = 64)
    private String roomNo;

    @Column(length = 64)
    private String contact;

    @Column(length = 64)
    private String priceDesc;

    private Integer status = 0;

    private Integer responseCount = 0;

    private Long dealOwnerId;

    private LocalDateTime dealTime;

    private LocalDateTime expireTime;

    private LocalDateTime createTime = LocalDateTime.now();

    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
