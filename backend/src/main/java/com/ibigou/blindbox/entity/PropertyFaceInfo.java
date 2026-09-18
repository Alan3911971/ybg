package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物业人脸信息表（property_face_info）
 */
@Entity
@Table(name = "property_face_info")
@Getter
@Setter
public class PropertyFaceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long faceId;

    @Column(nullable = false)
    private Long ownerId;

    @Column
    private Long memberId;

    @Column(nullable = false)
    private Long roomId;

    @Lob
    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] faceFeature;

    @Column(columnDefinition = "JSON")
    private String deviceIds;

    @Column
    private Integer auditStatus = 0;

    @Column
    private Long auditorId;

    @Column(length = 255)
    private String auditRemark;

    @Column
    private LocalDateTime auditTime;

    @Column
    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
