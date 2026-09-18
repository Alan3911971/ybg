package com.ibigou.blindbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物业增值服务表（property_value_service）
 */
@Entity
@Table(name = "property_value_service")
@Getter
@Setter
public class PropertyValueService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long serviceId;

    @Column(nullable = false)
    private Long companyId;

    private Long merchantId;

    @Column(nullable = false, length = 128)
    private String serviceName;

    @Column(nullable = false)
    private Integer serviceType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 16)
    private String priceUnit = "次";

    @Column(length = 500)
    private String description;

    @Column(length = 255)
    private String coverImage;

    @Column(precision = 5, scale = 2)
    private BigDecimal splitRatio = BigDecimal.valueOf(0);

    private Integer sortOrder = 0;

    private Integer status = 1;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
