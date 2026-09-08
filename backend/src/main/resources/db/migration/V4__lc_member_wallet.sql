-- V4: lc_member_wallet 表（余额按商家隔离）
-- 对应 WALLET-BE-002 / WALLET-TEST-004 方案 B
-- 主键：(member_id, store_id) 联合唯一

CREATE TABLE IF NOT EXISTS lc_member_wallet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id VARCHAR(20) NOT NULL COMMENT '会员手机号',
    store_id VARCHAR(64) NOT NULL COMMENT '商家编号',
    store_name VARCHAR(128) COMMENT '商家名称（冗余）',
    balance DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
    frozen_balance DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额',
    total_granted DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '累计发放金额',
    total_consumed DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '累计消费金额',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_store (member_id, store_id),
    INDEX idx_member_id (member_id),
    INDEX idx_store_id (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员按商家钱包余额表';
