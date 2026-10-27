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

    /** 顾客手机号（电视"最近中奖"显示尾号用；老数据回填，新数据随播报写入） */
    @Column(length = 20)
    private String phone;

    /** 奖品类型（1折扣券 2立减券 3余额 4团购免单 5谢谢参与；NULL=非中奖播报或历史数据） */
    @Column(name = "prize_type")
    private Integer prizeType;

    /** 奖品数值：折扣券=折数，立减券/余额=金额 */
    @Column(name = "prize_value")
    private java.math.BigDecimal prizeValue;

    /** 本店第几位顾客（= box_prize_limit_stat 该商家累计人次） */
    @Column(name = "customer_seq")
    private Integer customerSeq;

    /**
     * 是否权威开奖记录（1=是）。
     * 同一次抽奖会落多条 draw 记录（后端开奖段 + 前端结算页触发两次 announce-prize），
     * 只有一条标记为 1；电视大屏只认它，避免同一次中奖重复弹窗/重复上"最近中奖"列表。
     */
    @Column(name = "is_primary")
    private Integer isPrimary;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
