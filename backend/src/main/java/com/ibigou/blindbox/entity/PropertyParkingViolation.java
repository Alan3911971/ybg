package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 违停曝光（property_parking_violation）。
 * recognizeResult：1 登记车 / 2 访客 / 3 无登记
 * status：0 待处置 / 1 已挪走 / 2 待审曝光 / 3 已上曝光 / 4 误报 / 5 已关单
 */
@Entity
@Table(name = "property_parking_violation")
@Getter
@Setter
public class PropertyParkingViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vioId;

    @Column(nullable = false)
    private Long companyId;

    private Long communityId;

    @Column(nullable = false, length = 128)
    private String location;

    @Column(length = 20)
    private String carPlate;

    @Column(length = 20)
    private String plateMasked;

    private Long submitOwnerId;

    @Column(length = 64)
    private String submitRoomNo;

    /** [{type,url}] */
    @Column(columnDefinition = "JSON")
    private String media;

    private LocalDateTime shootTime;

    private Integer recognizeResult;

    private Long vehicleId;

    private Long ownerId;

    /** 1=通知车主 2=通知邀请人 3=转贴单 4=曝光 */
    private Integer action;

    private LocalDateTime notifyTime;

    private LocalDateTime moveAwayTime;

    private Integer minutesUsed;

    private Integer status = 0;

    @Column(columnDefinition = "TEXT")
    private String exposureDraft;

    private LocalDateTime exposurePubTime;

    private Long handlerId;

    @Column(length = 255)
    private String handleNote;

    private LocalDateTime createTime = LocalDateTime.now();

    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
