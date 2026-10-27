package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车位转让备案（property_parking_transfer）。
 * 三步：① 双方确认 ② 月租账户变更 ③ 道闸白名单生效；缺一步道闸进不去。
 */
@Entity
@Table(name = "property_parking_transfer")
@Getter
@Setter
public class PropertyParkingTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transferId;

    @Column(nullable = false)
    private Long companyId;

    private Long postId;

    @Column(nullable = false, length = 20)
    private String carPlate;

    @Column(length = 32)
    private String spaceNo;

    private Long fromOwnerId;

    @Column(length = 64)
    private String fromRoomNo;

    private Long toOwnerId;

    @Column(length = 64)
    private String toRoomNo;

    private Integer stepConfirm = 0;

    private Integer stepAccount = 0;

    private Integer stepWhitelist = 0;

    /** 0=进行中 1=已备案 2=已取消 */
    private Integer status = 0;

    @Column(precision = 10, scale = 2)
    private BigDecimal feeMonth;

    private LocalDateTime confirmTime;

    private LocalDateTime accountTime;

    private LocalDateTime whitelistTime;

    private Long operatorId;

    @Column(length = 255)
    private String remark;

    private LocalDateTime createTime = LocalDateTime.now();

    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
