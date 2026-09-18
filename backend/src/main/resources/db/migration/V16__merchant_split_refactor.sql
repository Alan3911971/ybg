-- ============================================================================
-- V16: 双模式分账模型（商家→物业 + 平台→物业）
-- 变更：
--   1. 新增 merchant_property_binding 表（含 split_mode 区分两种模式）
--   2. property_split_record 增加 merchant_no / split_mode 字段
--   3. 同一商家可同时启用两种模式（分别创建两条 binding 记录）
-- ============================================================================

-- 1. 商家-物业关联表（双模式）
CREATE TABLE IF NOT EXISTS merchant_property_binding (
    id              BIGINT UNSIGNED AUTO_INCREMENT,
    merchant_no     VARCHAR(64) NOT NULL COMMENT '商家编号',
    company_id      BIGINT UNSIGNED NOT NULL COMMENT '物业公司ID',
    split_mode      TINYINT NOT NULL DEFAULT 1 COMMENT '分账模式 1=商家→物业 2=平台→物业',
    custom_split_ratio DECIMAL(5,2) DEFAULT NULL COMMENT '自定义分账比例(%),NULL=用商家全局比例',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    remark          VARCHAR(255) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_company_mode (merchant_no, company_id, split_mode),
    KEY idx_company (company_id),
    KEY idx_merchant_mode_status (merchant_no, split_mode, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家-物业关联表(双模式)';

-- 2. property_split_record 增加字段（幂等）
SET @dbname = DATABASE();
SET @tbl = 'property_split_record';

-- 添加 merchant_no
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'merchant_no';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE property_split_record ADD COLUMN merchant_no VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''发起分账的商家编号'' AFTER merchant_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 添加 split_mode
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'split_mode';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE property_split_record ADD COLUMN split_mode TINYINT NOT NULL DEFAULT 1 COMMENT ''分账模式 1=商家→物业 2=平台→物业'' AFTER split_amount',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填已有数据
UPDATE property_split_record SET merchant_no = CONCAT('LEGACY_', merchant_id) WHERE merchant_no = '' AND merchant_id IS NOT NULL;

-- 3. 更新全局配置文案
UPDATE sys_global_config SET remark = '分账最大比例(%)，商家→物业和平台→物业共用上限' WHERE config_key = 'property_split_max_ratio';
UPDATE sys_global_config SET remark = '分账T+N结算天数(双模式通用)' WHERE config_key = 'property_split_settle_days';
