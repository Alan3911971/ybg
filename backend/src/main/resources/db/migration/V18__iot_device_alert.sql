-- 设备表
CREATE TABLE IF NOT EXISTS property_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    device_no VARCHAR(64) NOT NULL COMMENT '设备编号',
    device_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_type VARCHAR(30) NOT NULL COMMENT 'door/gate/elevator/fire/camera',
    location VARCHAR(200) COMMENT '安装位置',
    brand VARCHAR(50) COMMENT '品牌(hikvision/dahua/uniview)',
    ip_address VARCHAR(45) COMMENT 'IP地址',
    status TINYINT DEFAULT 1 COMMENT '1在线 0离线 2故障',
    last_heartbeat DATETIME COMMENT '最后心跳时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_no (device_no),
    INDEX idx_company (company_id),
    INDEX idx_community (community_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备';

-- 告警规则表
CREATE TABLE IF NOT EXISTS property_alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT '规则名称',
    device_type VARCHAR(30) COMMENT '适用设备类型(null=全部)',
    alert_type VARCHAR(30) NOT NULL COMMENT 'offline/fault/tamper/overtemp/custom',
    threshold INT DEFAULT 1 COMMENT '触发阈值(次数)',
    window_minutes INT DEFAULT 5 COMMENT '时间窗口(分钟)',
    silence_minutes INT DEFAULT 30 COMMENT '静默期(分钟)',
    auto_workorder TINYINT DEFAULT 1 COMMENT '自动创建工单',
    notify_wechat TINYINT DEFAULT 0 COMMENT '微信推送',
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则';

-- 告警记录表
CREATE TABLE IF NOT EXISTS property_device_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    alert_type VARCHAR(30) NOT NULL,
    level TINYINT DEFAULT 1 COMMENT '1info 2warning 3critical',
    message VARCHAR(500) NOT NULL,
    raw_data TEXT COMMENT '原始上报数据JSON',
    rule_id BIGINT COMMENT '触发的规则ID',
    workorder_id BIGINT COMMENT '关联工单ID',
    ack_status TINYINT DEFAULT 0 COMMENT '0未确认 1已确认 2已处理',
    ack_by BIGINT COMMENT '确认人adminId',
    ack_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device (device_id),
    INDEX idx_company_time (company_id, create_time),
    INDEX idx_ack (ack_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备告警记录';
