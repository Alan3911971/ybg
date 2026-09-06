package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 宜必购渠道商品（ibigou_goods），支撑商品浏览/下单。
 * 商家可上架自有商品，顾客在商城看到所有商家的产品。
 */
@Entity
@Table(name = "ibigou_goods")
@Getter
@Setter
public class IbigouGoods {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long goodsId;

    @Column(nullable = false, length = 32)
    private String merchantNo;

    @Column(nullable = false, length = 128)
    private String goodsName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 512)
    private String description;

    @Column(length = 1024)
    private String images;

    @Column(nullable = false)
    private Integer enabled;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Integer salesCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;
}
