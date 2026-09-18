-- 场地/设施表
CREATE TABLE IF NOT EXISTS property_facility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT '场地名称',
    description VARCHAR(500) COMMENT '描述',
    location VARCHAR(200) COMMENT '位置',
    capacity INT DEFAULT 0 COMMENT '容纳人数',
    price DECIMAL(10,2) DEFAULT 0 COMMENT '每小时费用',
    open_time TIME NOT NULL COMMENT '开放开始时间',
    close_time TIME NOT NULL COMMENT '开放结束时间',
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业场地/设施';

-- 预约记录表
CREATE TABLE IF NOT EXISTS property_reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_no VARCHAR(32) NOT NULL UNIQUE COMMENT '预约编号',
    facility_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    reserve_date DATE NOT NULL COMMENT '预约日期',
    start_time TIME NOT NULL COMMENT '开始时段',
    end_time TIME NOT NULL COMMENT '结束时段',
    amount DECIMAL(10,2) DEFAULT 0 COMMENT '费用',
    pay_status TINYINT DEFAULT 0 COMMENT '0未付 1已付 2已退',
    status TINYINT DEFAULT 0 COMMENT '0待确认 1已确认 2已签到 3已取消 4已完成',
    remark VARCHAR(300) COMMENT '备注',
    cancel_reason VARCHAR(300) COMMENT '取消原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_facility_date (facility_id, reserve_date),
    INDEX idx_owner (owner_id),
    INDEX idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地预约记录';
