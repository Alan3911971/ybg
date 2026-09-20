package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 商家排队号表
 */
@Entity
@Table(name = "merchant_queue_ticket")
@Getter
@Setter
public class MerchantQueueTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketId;

    @Column(nullable = false, length = 64)
    private String merchantNo;

    /** 排队号，如 A001 */
    @Column(nullable = false, length = 16)
    private String ticketNo;

    @Column(length = 20)
    private String phone;

    /** 人数/桌型，如 2/4/6/8 */
    private Integer seats;

    /** 窗口号 */
    private Integer windowNo;

    /** 0等待 1叫号中 2完成 3过号 */
    @Column(nullable = false)
    private Integer status = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    private LocalDateTime callTime;

    private LocalDateTime finishTime;
}
