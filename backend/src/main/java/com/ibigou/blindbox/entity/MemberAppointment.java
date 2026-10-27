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

    /** 顾客留言（预约时提交，等待商家回复） */
    @Column(length = 300)
    private String customerMsg;

    /** 0待服务 1已完成 2已取消 */
    @Column(nullable = false)
    private Integer status;

    /** 回复状态：0待回复 1已回复 */
    @Column(nullable = false)
    private Integer replyStatus;

    /** 商家回复内容 */
    @Column(length = 300)
    private String replyContent;

    /** 回复时间 */
    private LocalDateTime replyTime;

    /** 回复人（商家登录账号 / AI） */
    @Column(length = 64)
    private String replyBy;

    /** 回复已触达顾客：0否 1是 */
    @Column(nullable = false)
    private Integer replySent;

    /** 触达通道：user_message站内 / sms短信 / both */
    @Column(length = 32)
    private String replyChannel;

    /** 提前提醒分钟数 */
    @Column(nullable = false)
    private Integer remindMinutes;

    /** 提醒已发送 0否 1是 */
    @Column(nullable = false)
    private Integer remindSent;

    @Column(nullable = false)
    private LocalDateTime createTime;
}
