package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 商家电视广告素材
 */
@Entity
@Table(name = "merchant_ad")
@Getter
@Setter
public class MerchantAd {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adId;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    @Column(length = 128)
    private String title;

    /** image / video */
    @Column(nullable = false, length = 16)
    private String type = "image";

    /** 素材 URL */
    @Column(nullable = false, length = 512)
    private String url;

    /** 投放范围：0仅自家 1公共池 */
    @Column(nullable = false)
    private Integer scope = 0;

    /** 审核状态：0待审 1通过 2驳回（仅公共池需要审核） */
    @Column(nullable = false)
    private Integer auditStatus = 0;

    @Column(length = 256)
    private String auditRemark;

    /** 是否启用 0下架 1启用 */
    @Column(nullable = false)
    private Integer enabled = 1;

    /** 排序，越小越靠前 */
    @Column(nullable = false)
    private Integer sort = 0;

    /** 停留时长（秒），图片用 */
    @Column(nullable = false)
    private Integer durationSec = 10;

    /** 播放开始时间 */
    private LocalDateTime startTime;

    /** 播放结束时间 */
    private LocalDateTime endTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
