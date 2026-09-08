package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V8 会员资料（member_profile）：客户喜好 / 家人喜好 / 备注。
 */
@Entity
@Table(name = "member_profile")
@Getter
@Setter
public class MemberProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String merchantNo;

    @Column(nullable = false, length = 20)
    private String userPhone;

    @Column(length = 32)
    private String name;

    @Column(length = 8)
    private String gender;

    @Column(length = 16)
    private String memberType;

    /** 客户喜好 */
    @Column(length = 1000)
    private String customerPref;

    /** 家人喜好 */
    @Column(length = 1000)
    private String familyPref;

    @Column(length = 10)
    private String birthday;

    @Column(length = 500)
    private String remark;

    @Column(length = 64)
    private String editor;

    @Column
    private LocalDateTime updateTime;
}
