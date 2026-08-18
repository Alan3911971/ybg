package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V1.5 P2：商家播报事件（announce_log）。
 * 开奖/订单完成/退款/兜底话术记录；商家 H5 轮询后语音朗读（Web Speech API / 音箱服务端推送）。
 */
@Entity
@Table(name = "announce_log")
@Getter
@Setter
public class AnnounceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long announceId;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    /** draw / order_auto / order_manual / refund / manual_complete */
    @Column(nullable = false, length = 32)
    private String eventType;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
