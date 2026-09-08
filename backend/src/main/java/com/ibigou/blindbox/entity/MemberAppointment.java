package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V8 会员预约（member_appointment）：预约时间 / 服务项目 / 提醒。
 */
@Entity
@Table(name = "member_appointment")
@Getter
@Setter
public class MemberAppointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String merchantNo;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 预约时间 */
    @Column(nullable = false)
    private LocalDateTime apptTime;

    /** 服务项目 */
    @Column(length = 100)
    private String serviceItem;

    @Column(length = 200)
    private String remark;

    /** 0待服务 1已完成 2已取消 */
    @Column(nullable = false)
    private Integer status;

    /** 提前提醒分钟数 */
    @Column(nullable = false)
    private Integer remindMinutes;

    /** 提醒已发送 0否 1是 */
    @Column(nullable = false)
    private Integer remindSent;

    @Column(nullable = false)
    private LocalDateTime createTime;
}
