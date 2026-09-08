package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V8 会员回访记录（member_visit）。
 */
@Entity
@Table(name = "member_visit")
@Getter
@Setter
public class MemberVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String merchantNo;

    @Column(nullable = false, length = 20)
    private String userPhone;

    /** 回访内容 */
    @Column(nullable = false, length = 500)
    private String content;

    /** 回访结果 */
    @Column(length = 200)
    private String result;

    /** 回访时间 */
    @Column
    private LocalDateTime visitTime;

    @Column(nullable = false)
    private LocalDateTime createTime;
}
