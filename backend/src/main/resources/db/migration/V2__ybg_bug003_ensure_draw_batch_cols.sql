-- ============================================================================
-- V2 ?? YBG-BIZ-BUG-003(bug1): ?? draw_batch_no / merchant_no ???
--
-- ???application.yml flyway baseline-on-migrate=true + baseline-version=1?
-- ????????? baseline ? V1??? V1__baseline.sql ??
-- sp_add_draw_batch_no() ????????????????
-- user_coupon.draw_batch_no / user_balance_flow.draw_batch_no / merchant_no ???
-- ???? compensateDrawBatch ?????? -> ??? ErrorHandler -> ?? JVM?
--
-- ????????????????? V1 ??????? V1 ? baseline ??????????
-- ============================================================================

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_ybg_bug003_ensure_cols$$
CREATE PROCEDURE sp_ybg_bug003_ensure_cols()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_coupon' AND COLUMN_NAME = 'draw_batch_no') THEN
    ALTER TABLE user_coupon ADD COLUMN draw_batch_no VARCHAR(64) DEFAULT NULL COMMENT '?????(??????can_use_after_draw=1)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_coupon' AND INDEX_NAME = 'idx_coupon_batch') THEN
    ALTER TABLE user_coupon ADD KEY idx_coupon_batch (draw_batch_no);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_balance_flow' AND COLUMN_NAME = 'draw_batch_no') THEN
    ALTER TABLE user_balance_flow ADD COLUMN draw_batch_no VARCHAR(64) DEFAULT NULL COMMENT '?????(??????can_use_after_draw=1)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_balance_flow' AND INDEX_NAME = 'idx_flow_batch') THEN
    ALTER TABLE user_balance_flow ADD KEY idx_flow_batch (draw_batch_no);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_balance_flow' AND COLUMN_NAME = 'merchant_no') THEN
    ALTER TABLE user_balance_flow ADD COLUMN merchant_no VARCHAR(64) DEFAULT NULL COMMENT '????/????(?????,V1.4??)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_balance_flow' AND INDEX_NAME = 'idx_flow_merchant') THEN
    ALTER TABLE user_balance_flow ADD KEY idx_flow_merchant (merchant_no, create_time);
  END IF;
END$$
DELIMITER ;

CALL sp_ybg_bug003_ensure_cols();
DROP PROCEDURE sp_ybg_bug003_ensure_cols;
