-- ============================================================================
-- V15: 物业系统 P1 增量建表 (10张表 + 全局配置)
-- ============================================================================

-- 1. 工单表
CREATE TABLE IF NOT EXISTS property_workorder (
    wo_id           BIGINT UNSIGNED AUTO_INCREMENT,
    wo_no           VARCHAR(64) NOT NULL,
    owner_id        BIGINT UNSIGNED NOT NULL,
    member_id       BIGINT UNSIGNED DEFAULT NULL,
    company_id      BIGINT UNSIGNED NOT NULL,
    room_id         BIGINT UNSIGNED DEFAULT NULL,
    wo_type         TINYINT NOT NULL COMMENT '1报修 2投诉 3建议 4咨询',
    title           VARCHAR(128) NOT NULL,
    description     TEXT,
    images          JSON DEFAULT NULL,
    urgency         TINYINT DEFAULT 1,
    category        VARCHAR(64) DEFAULT NULL,
    assignee_id     BIGINT UNSIGNED DEFAULT NULL,
    assignee_name   VARCHAR(64) DEFAULT NULL,
    status          TINYINT DEFAULT 0 COMMENT '0待接单 1处理中 2待验收 3已完成 4已关闭 5已转派',
    rating          TINYINT DEFAULT NULL,
    rating_comment  VARCHAR(500) DEFAULT NULL,
    completed_time  DATETIME DEFAULT NULL,
    rated_time      DATETIME DEFAULT NULL,
    timeout_count   INT DEFAULT 0,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (wo_id),
    UNIQUE KEY uk_wo_no (wo_no),
    KEY idx_owner (owner_id),
    KEY idx_company (company_id),
    KEY idx_assignee (assignee_id),
    KEY idx_status (status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';

-- 2. 工单操作日志
CREATE TABLE IF NOT EXISTS property_wo_log (
    log_id          BIGINT UNSIGNED AUTO_INCREMENT,
    wo_id           BIGINT UNSIGNED NOT NULL,
    operator_id     BIGINT UNSIGNED DEFAULT NULL,
    operator_name   VARCHAR(64) NOT NULL,
    action          VARCHAR(32) NOT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    before_status   TINYINT DEFAULT NULL,
    after_status    TINYINT DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_wo (wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单操作日志';

-- 3. 巡检计划
CREATE TABLE IF NOT EXISTS property_inspection_plan (
    plan_id         BIGINT UNSIGNED AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    plan_name       VARCHAR(128) NOT NULL,
    route_desc      TEXT,
    checkpoint_ids  JSON DEFAULT NULL,
    frequency       TINYINT NOT NULL COMMENT '1每日 2每周 3每月',
    schedule_time   VARCHAR(32) DEFAULT NULL,
    assignee_ids    JSON DEFAULT NULL,
    status          TINYINT DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (plan_id),
    KEY idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检计划';

-- 4. 巡检记录
CREATE TABLE IF NOT EXISTS property_inspection_record (
    record_id       BIGINT UNSIGNED AUTO_INCREMENT,
    plan_id         BIGINT UNSIGNED NOT NULL,
    inspector_id    BIGINT UNSIGNED NOT NULL,
    inspector_name  VARCHAR(64) NOT NULL,
    company_id      BIGINT UNSIGNED NOT NULL,
    check_date      DATE NOT NULL,
    check_time      DATETIME NOT NULL,
    location_lat    DECIMAL(10,6) DEFAULT NULL,
    location_lng    DECIMAL(10,6) DEFAULT NULL,
    checkpoint_id   VARCHAR(64) DEFAULT NULL,
    result          TINYINT NOT NULL COMMENT '1正常 2异常',
    issue_desc      VARCHAR(500) DEFAULT NULL,
    issue_images    JSON DEFAULT NULL,
    wo_id           BIGINT UNSIGNED DEFAULT NULL,
    status          TINYINT DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (record_id),
    KEY idx_plan (plan_id),
    KEY idx_company_date (company_id, check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检记录';

-- 5. 增值服务项目
CREATE TABLE IF NOT EXISTS property_value_service (
    service_id      BIGINT UNSIGNED AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    merchant_id     BIGINT UNSIGNED DEFAULT NULL,
    service_name    VARCHAR(128) NOT NULL,
    service_type    TINYINT NOT NULL COMMENT '1维修 2家政 3保洁 4搬家 5其他',
    unit_price      DECIMAL(10,2) NOT NULL,
    price_unit      VARCHAR(16) NOT NULL DEFAULT '次',
    description     VARCHAR(500) DEFAULT NULL,
    cover_image     VARCHAR(255) DEFAULT NULL,
    split_ratio     DECIMAL(5,2) DEFAULT 0,
    sort_order      INT DEFAULT 0,
    status          TINYINT DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (service_id),
    KEY idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='增值服务项目';

-- 6. 增值服务订单
CREATE TABLE IF NOT EXISTS property_vs_order (
    order_id        BIGINT UNSIGNED AUTO_INCREMENT,
    order_no        VARCHAR(64) NOT NULL,
    owner_id        BIGINT UNSIGNED NOT NULL,
    company_id      BIGINT UNSIGNED NOT NULL,
    service_id      BIGINT UNSIGNED NOT NULL,
    merchant_id     BIGINT UNSIGNED DEFAULT NULL,
    room_id         BIGINT UNSIGNED DEFAULT NULL,
    quantity        INT NOT NULL DEFAULT 1,
    unit_price      DECIMAL(10,2) NOT NULL,
    total_amount    DECIMAL(12,2) NOT NULL,
    paid_amount     DECIMAL(12,2) DEFAULT 0,
    deduct_amount   DECIMAL(12,2) DEFAULT 0,
    pay_channel     VARCHAR(32) DEFAULT NULL,
    appointment_time DATETIME DEFAULT NULL,
    status          TINYINT DEFAULT 0,
    wx_transaction_id VARCHAR(128) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_owner (owner_id),
    KEY idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='增值服务订单';

-- 7. 人脸信息
CREATE TABLE IF NOT EXISTS property_face_info (
    face_id         BIGINT UNSIGNED AUTO_INCREMENT,
    owner_id        BIGINT UNSIGNED NOT NULL,
    member_id       BIGINT UNSIGNED DEFAULT NULL,
    room_id         BIGINT UNSIGNED NOT NULL,
    face_feature    MEDIUMBLOB DEFAULT NULL,
    device_ids      JSON DEFAULT NULL,
    audit_status    TINYINT DEFAULT 0,
    auditor_id      BIGINT UNSIGNED DEFAULT NULL,
    audit_remark    VARCHAR(255) DEFAULT NULL,
    audit_time      DATETIME DEFAULT NULL,
    status          TINYINT DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (face_id),
    KEY idx_owner (owner_id),
    KEY idx_audit_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人脸信息';

-- 8. 访客车辆预约
CREATE TABLE IF NOT EXISTS property_visitor_vehicle (
    visit_id        BIGINT UNSIGNED AUTO_INCREMENT,
    owner_id        BIGINT UNSIGNED NOT NULL,
    plate_no        VARCHAR(20) NOT NULL,
    visitor_name    VARCHAR(64) DEFAULT NULL,
    visitor_phone   VARCHAR(20) DEFAULT NULL,
    community_id    BIGINT UNSIGNED NOT NULL,
    room_id         BIGINT UNSIGNED DEFAULT NULL,
    arrive_time     DATETIME NOT NULL,
    expire_time     DATETIME NOT NULL,
    status          TINYINT DEFAULT 1,
    used_time       DATETIME DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (visit_id),
    KEY idx_owner (owner_id),
    KEY idx_plate (plate_no),
    KEY idx_expire (expire_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客车辆预约';

-- 9. 临时车缴费
CREATE TABLE IF NOT EXISTS property_temp_parking_payment (
    payment_id      BIGINT UNSIGNED AUTO_INCREMENT,
    payment_no      VARCHAR(64) NOT NULL,
    vehicle_log_id  BIGINT UNSIGNED NOT NULL,
    plate_no        VARCHAR(20) NOT NULL,
    community_id    BIGINT UNSIGNED NOT NULL,
    duration_min    INT NOT NULL,
    fee_amount      DECIMAL(10,2) NOT NULL,
    paid_amount     DECIMAL(10,2) NOT NULL,
    pay_channel     VARCHAR(32) NOT NULL,
    wx_transaction_id VARCHAR(128) DEFAULT NULL,
    pay_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    split_amount    DECIMAL(10,2) DEFAULT 0,
    status          TINYINT DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_plate (plate_no),
    KEY idx_community (community_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='临时车缴费';

-- 10. 无感支付绑定
CREATE TABLE IF NOT EXISTS property_auto_pay_bindding (
    bind_id         BIGINT UNSIGNED AUTO_INCREMENT,
    owner_id        BIGINT UNSIGNED NOT NULL,
    plate_no        VARCHAR(20) NOT NULL,
    pay_channel     VARCHAR(32) NOT NULL,
    agreement_no    VARCHAR(128) DEFAULT NULL,
    status          TINYINT DEFAULT 1,
    sign_time       DATETIME DEFAULT NULL,
    unsign_time     DATETIME DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (bind_id),
    UNIQUE KEY uk_owner_plate (owner_id, plate_no),
    KEY idx_plate (plate_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无感支付绑定';

-- P1 全局配置
INSERT INTO sys_global_config (config_key, config_value, remark) VALUES
('wo_timeout_escalate_minutes', '120', '工单超时升级阈值(分钟)'),
('wo_auto_close_days', '7', '已完成工单自动关闭天数'),
('inspection_check_radius_meters', '50', '巡检打卡有效半径(米)'),
('vs_default_split_ratio', '15', '增值服务默认分账比例(%)'),
('temp_parking_split_enabled', '1', '临时车收入是否纳入分账池'),
('temp_parking_split_ratio', '10', '临时车分账比例(%)'),
('face_audit_required', '1', '人脸是否需要物业审核')
ON DUPLICATE KEY UPDATE config_key = config_key;
