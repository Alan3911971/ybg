package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 微信用户 openid 映射（手机号→openid）
 */
@Entity
@Table(name = "wx_user_openid")
@Getter
@Setter
public class WxUserOpenid {

    @Id
    @Column(length = 20)
    private String phone;

    @Column(length = 64)
    private String openid;

    @Column(length = 64)
    private String unionid;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
