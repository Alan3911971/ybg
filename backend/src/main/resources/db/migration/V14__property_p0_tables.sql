-- ============================================================================
-- V14: 物业系统 P0 建表 (18张表 + Merchant扩展字段 + 全局配置)
-- ============================================================================

-- 1. 物业公司
CREATE TABLE IF NOT EXISTS property_company (
    company_id      BIGINT UNSIGNED AUTO_INCREMENT,
    company_name    VARCHAR(128) NOT NULL,
    wx_mch_id       VARCHAR(64) DEFAULT NULL COMMENT '微信子商户号',
    contact_name    VARCHAR(64) DEFAULT NULL,
    contact_phone   VARCHAR(20) DEFAULT NULL,
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业公司';

-- 2. 小区
CREATE TABLE IF NOT EXISTS property_community (
    community_id    BIGINT UNSIGNED AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    community_name  VARCHAR(128) NOT NULL,
    address         VARCHAR(255) DEFAULT NULL,
    total_buildings INT DEFAULT 0,
    total_rooms     INT DEFAULT 0,
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (community_id),
    KEY idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小区';

-- 3. 楼栋
CREATE TABLE IF NOT EXISTS property_building (
    building_id     BIGINT UNSIGNED AUTO_INCREMENT,
    community_id    BIGINT UNSIGNED NOT NULL,
    building_name   VARCHAR(64) NOT NULL,
    total_units     INT DEFAULT 1,
    total_floors    INT DEFAULT 1,
    rooms_per_floor INT DEFAULT 1,
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (building_id),
    KEY idx_community (community_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼栋';

-- 4. 房屋
CREATE TABLE IF NOT EXISTS property_room (
    room_id         BIGINT UNSIGNED AUTO_INCREMENT,
    building_id     BIGINT UNSIGNED NOT NULL,
    unit_no         INT NOT NULL DEFAULT 1,
    floor_no        INT NOT NULL,
    room_no         VARCHAR(16) NOT NULL,
    area            DECIMAL(8,2) DEFAULT NULL,
    owner_name      VARCHAR(64) DEFAULT NULL,
    owner_phone     VARCHAR(20) DEFAULT NULL,
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id),
    KEY idx_building (building_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房屋';

-- 5. 业主
CREATE TABLE IF NOT EXISTS property_owner (
    owner_id        BIGINT UNSIGNED AUTO_INCREMENT,
    owner_no        VARCHAR(64) NOT NULL,
    owner_name      VARCHAR(64) NOT NULL,
    owner_phone     VARCHAR(20) NOT NULL,
    password        VARCHAR(128) NOT NULL,
    balance         DECIMAL(12,2) NOT NULL DEFAULT 0,
    pending_split   DECIMAL(12,2) NOT NULL DEFAULT 0,
    carry_over      DECIMAL(12,2) NOT NULL DEFAULT 0,
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_id),
    UNIQUE KEY uk_owner_phone (owner_phone),
    UNIQUE KEY uk_owner_no (owner_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业主';

-- 6. 绑定关系
CREATE TABLE IF NOT EXISTS property_bind_relation (
    bind_id         BIGINT UNSIGNED AUTO_INCREMENT,
    owner_id        BIGINT UNSIGNED NOT NULL,
    company_id      BIGINT UNSIGNED NOT NULL,
    bind_time       DATETIME NOT NULL,
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (bind_id),
    KEY idx_owner (owner_id),
    KEY idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业主-物业绑定关系';

-- 7. 家庭成员
CREATE TABLE IF NOT EXISTS property_family_member (
    member_id       BIGINT UNSIGNED AUTO_INCREMENT,
    owner_id        BIGINT UNSIGNED NOT NULL,
    member_name     VARCHAR(64) NOT NULL,
    member_phone    VARCHAR(20) DEFAULT NULL,
    relation        VARCHAR(32) DEFAULT NULL,
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭成员';

-- 8. 账单
CREATE TABLE IF NOT EXISTS property_bill (
    bill_id         BIGINT UNSIGNED AUTO_INCREMENT,
    bill_no         VARCHAR(64) NOT NULL,
    owner_id        BIGINT UNSIGNED NOT NULL,
    company_id      BIGINT UNSIGNED NOT NULL,
    room_id         BIGINT UNSIGNED DEFAULT NULL,
    bill_type       TINYINT NOT NULL COMMENT '1物业费 2车位费 3垃圾清运费 4维修基金 5其他',
    bill_period     VARCHAR(32) NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    deducted        DECIMAL(10,2) NOT NULL DEFAULT 0,
    paid            DECIMAL(10,2) NOT NULL DEFAULT 0,
    due_date        DATE NOT NULL,
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0待缴 1部分抵扣 2已结清 3已逾期',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (bill_id),
    UNIQUE KEY uk_bill_no (bill_no),
    KEY idx_owner_company (owner_id, company_id),
    KEY idx_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业账单';

-- 9. 分账记录
CREATE TABLE IF NOT EXISTS property_split_record (
    split_id        BIGINT UNSIGNED AUTO_INCREMENT,
    order_no        VARCHAR(64) NOT NULL,
    owner_id        BIGINT UNSIGNED NOT NULL,
    merchant_id     BIGINT UNSIGNED NOT NULL,
    company_id      BIGINT UNSIGNED NOT NULL,
    order_amount    DECIMAL(12,2) NOT NULL,
    split_ratio     DECIMAL(5,2) NOT NULL,
    split_amount    DECIMAL(12,2) NOT NULL,
    split_status    TINYINT NOT NULL DEFAULT 0 COMMENT '0待结算 1已结算 2已抵扣 3退款冲正 4失败',
    wx_transaction_id VARCHAR(128) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (split_id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_owner (owner_id),
    KEY idx_company (company_id),
    KEY idx_status (split_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分账记录';

-- 10. 抵扣日志
CREATE TABLE IF NOT EXISTS property_deduct_log (
    log_id          BIGINT UNSIGNED AUTO_INCREMENT,
    owner_id        BIGINT UNSIGNED NOT NULL,
    bill_id         BIGINT UNSIGNED DEFAULT NULL,
    parking_fee_id  BIGINT UNSIGNED DEFAULT NULL,
    deduct_amount   DECIMAL(10,2) NOT NULL,
    before_balance  DECIMAL(12,2) NOT NULL,
    after_balance   DECIMAL(12,2) NOT NULL,
    source_type     TINYINT NOT NULL COMMENT '1当期分账 2历年结转 3手动',
    source_split_id BIGINT UNSIGNED DEFAULT NULL,
    remark          VARCHAR(255) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_owner (owner_id),
    KEY idx_bill (bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抵扣日志';

-- 11. 活动审核
CREATE TABLE IF NOT EXISTS property_activity_audit (
    audit_id        BIGINT UNSIGNED AUTO_INCREMENT,
    merchant_id     BIGINT UNSIGNED NOT NULL,
    activity_name   VARCHAR(128) NOT NULL,
    discount_level  DECIMAL(5,2) DEFAULT NULL,
    start_time      DATETIME NOT NULL,
    end_time        DATETIME NOT NULL,
    apply_reason    VARCHAR(500) DEFAULT NULL,
    audit_status    TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1通过 2驳回',
    auditor_id      BIGINT UNSIGNED DEFAULT NULL,
    audit_remark    VARCHAR(255) DEFAULT NULL,
    audit_time      DATETIME DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    KEY idx_merchant (merchant_id),
    KEY idx_status (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动审核';

-- 12. 物业管理员
CREATE TABLE IF NOT EXISTS property_admin (
    admin_id        BIGINT UNSIGNED AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    account         VARCHAR(64) NOT NULL,
    password        VARCHAR(128) NOT NULL,
    real_name       VARCHAR(64) DEFAULT NULL,
    role            TINYINT NOT NULL DEFAULT 1 COMMENT '1普通 2超级',
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (admin_id),
    UNIQUE KEY uk_account (account),
    KEY idx_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业管理员';

-- 13. 管理员会话
CREATE TABLE IF NOT EXISTS property_admin_session (
    token           VARCHAR(128) NOT NULL,
    admin_id        BIGINT UNSIGNED NOT NULL,
    expire_time     DATETIME NOT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token),
    KEY idx_admin (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员会话';

-- 14. 门禁凭证
CREATE TABLE IF NOT EXISTS property_access_credential (
    cred_id         BIGINT UNSIGNED AUTO_INCREMENT,
    owner_id        BIGINT UNSIGNED NOT NULL,
    room_id         BIGINT UNSIGNED NOT NULL,
    cred_type       TINYINT NOT NULL COMMENT '1业主二维码 2访客码',
    cred_value      VARCHAR(128) NOT NULL,
    max_use_count   INT DEFAULT NULL,
    used_count      INT NOT NULL DEFAULT 0,
    expire_time     DATETIME NOT NULL,
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (cred_id),
    KEY idx_owner (owner_id),
    KEY idx_cred_value (cred_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门禁凭证';

-- 15. 通行日志
CREATE TABLE IF NOT EXISTS property_access_log (
    log_id          BIGINT UNSIGNED AUTO_INCREMENT,
    community_id    BIGINT UNSIGNED NOT NULL,
    credential_id   BIGINT UNSIGNED DEFAULT NULL,
    pass_type       TINYINT NOT NULL COMMENT '1二维码 2访客码 3远程开门 4人脸',
    direction       TINYINT NOT NULL COMMENT '1进 2出',
    device_id       VARCHAR(64) DEFAULT NULL,
    pass_time       DATETIME NOT NULL,
    phone_masked    VARCHAR(20) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_community_time (community_id, pass_time),
    KEY idx_credential (credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通行日志';

-- 16. 车辆
CREATE TABLE IF NOT EXISTS property_vehicle (
    vehicle_id      BIGINT UNSIGNED AUTO_INCREMENT,
    owner_id        BIGINT UNSIGNED NOT NULL,
    room_id         BIGINT UNSIGNED DEFAULT NULL,
    plate_no        VARCHAR(20) NOT NULL,
    plate_color     TINYINT NOT NULL DEFAULT 1 COMMENT '1蓝 2黄 3绿 4白',
    park_type       TINYINT NOT NULL DEFAULT 1 COMMENT '1月卡 2年卡',
    valid_start     DATE NOT NULL,
    valid_end       DATE NOT NULL,
    status          TINYINT NOT NULL DEFAULT 1,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (vehicle_id),
    KEY idx_owner (owner_id),
    KEY idx_plate (plate_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆';

-- 17. 车位费
CREATE TABLE IF NOT EXISTS property_parking_fee (
    fee_id          BIGINT UNSIGNED AUTO_INCREMENT,
    fee_no          VARCHAR(64) NOT NULL,
    owner_id        BIGINT UNSIGNED NOT NULL,
    company_id      BIGINT UNSIGNED NOT NULL,
    vehicle_id      BIGINT UNSIGNED DEFAULT NULL,
    period          VARCHAR(32) NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    deducted        DECIMAL(10,2) NOT NULL DEFAULT 0,
    paid            DECIMAL(10,2) NOT NULL DEFAULT 0,
    due_date        DATE NOT NULL,
    status          TINYINT NOT NULL DEFAULT 0,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (fee_id),
    UNIQUE KEY uk_fee_no (fee_no),
    KEY idx_owner_company (owner_id, company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车位费';

-- 18. 车辆通行日志
CREATE TABLE IF NOT EXISTS property_vehicle_log (
    log_id          BIGINT UNSIGNED AUTO_INCREMENT,
    community_id    BIGINT UNSIGNED NOT NULL,
    plate_no        VARCHAR(20) NOT NULL,
    enter_time      DATETIME DEFAULT NULL,
    exit_time       DATETIME DEFAULT NULL,
    direction       TINYINT NOT NULL COMMENT '1进 2出',
    device_id       VARCHAR(64) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_plate (plate_no),
    KEY idx_community_time (community_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆通行日志';

-- Merchant 扩展字段（幂等）
SET @dbname = DATABASE();
SET @tablename = 'merchant';

SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tablename AND column_name = 'is_split_enabled';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE merchant ADD COLUMN is_split_enabled TINYINT NOT NULL DEFAULT 0 COMMENT ''物业分账:是否参与'', ADD COLUMN split_ratio DECIMAL(5,2) DEFAULT NULL COMMENT ''物业分账:比例(%)'', ADD COLUMN wx_sub_mch_id VARCHAR(64) DEFAULT NULL COMMENT ''物业分账:微信子商户号'', ADD COLUMN split_audit_status TINYINT NOT NULL DEFAULT 0 COMMENT ''物业分账:开通状态''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 全局配置
INSERT INTO sys_global_config (config_key, config_value, remark) VALUES
('property_split_max_ratio', '30', '物业分账最大比例(%)'),
('property_deduct_priority', 'bind_time', '抵扣优先级规则'),
('property_balance_carry_over', '1', '余额是否跨年结转 0否 1是'),
('property_access_qr_expire_seconds', '120', '开门二维码有效期(秒)'),
('property_visitor_code_expire_hours', '24', '访客码默认有效期(小时)'),
('property_monthly_card_auto_renew', '0', '月卡是否自动续费 0否 1是'),
('property_bill_overdue_days', '30', '账单逾期天数阈值'),
('property_split_settle_days', '7', '分账T+N结算天数'),
('property_face_required', '0', '是否强制人脸开门 0否 1是'),
('property_temp_parking_free_minutes', '30', '临时车免费时长(分钟)')
ON DUPLICATE KEY UPDATE config_key = config_key;
