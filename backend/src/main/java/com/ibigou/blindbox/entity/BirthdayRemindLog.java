package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V19 生日提醒幂等记录（birthday_remind_log）。
 * <p>按 商家+会员+年份+提前天数 唯一，保证同一年同一档位只提醒一次、只发一次关怀券。</p>
 */
@Entity
@Table(name = "birthday_remind_log",
       uniqueConstraints = @UniqueConstraint(columnNames = {"merchantNo", "userPhone", "remindYear", "daysAhead"}))
@Getter
@Setter
public class BirthdayRemindLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String merchantNo;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 提醒年份（YYYY） */
    @Column(nullable = false)
    private Integer remindYear;

    /** 提前天数（7/3/1/0） */
    @Column(nullable = false)
    private Integer daysAhead;

    /** 已发放的生日关怀券ID */
    private Long couponId;

    @Column(nullable = false)
    private LocalDateTime createTime;
}
