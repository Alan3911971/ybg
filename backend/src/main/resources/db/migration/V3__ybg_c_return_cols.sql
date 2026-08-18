-- ============================================================================
-- V3 ?? YBG-C-RETURN-001: third_group_verify_record ?? 3 ?
--   老库升级：draw_batch_no / return_balance / return_desc（幂等）
-- ============================================================================

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_ybg_c_return_cols$$
CREATE PROCEDURE sp_ybg_c_return_cols()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'third_group_verify_record' AND COLUMN_NAME = 'draw_batch_no') THEN
    ALTER TABLE third_group_verify_record ADD COLUMN draw_batch_no VARCHAR(64) DEFAULT NULL COMMENT '抽奖批次号(折算防重)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'third_group_verify_record' AND COLUMN_NAME = 'return_balance') THEN
    ALTER TABLE third_group_verify_record ADD COLUMN return_balance DECIMAL(10,2) DEFAULT 0.00 COMMENT '登记折算赠送余额';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'third_group_verify_record' AND COLUMN_NAME = 'return_desc') THEN
    ALTER TABLE third_group_verify_record ADD COLUMN return_desc VARCHAR(255) DEFAULT NULL COMMENT '折算赠送说明';
  END IF;
END$$
DELIMITER ;

CALL sp_ybg_c_return_cols();
DROP PROCEDURE IF EXISTS sp_ybg_c_return_cols;
