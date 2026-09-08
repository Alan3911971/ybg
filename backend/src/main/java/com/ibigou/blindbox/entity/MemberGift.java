package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会员礼品赠送记录（member_gift）。
 * 店长在生日或其他时间赠送商品库中任意商品给客户。
 */
@Entity
@Table(name = "member_gift")
@Getter
@Setter
public class MemberGift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String merchantNo;

    @Column(nullable = false, length = 20)
    private String userPhone;

    private Long goodsId;

    @Column(nullable = false, length = 128)
    private String goodsName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 500)
    private String remark;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;
}
