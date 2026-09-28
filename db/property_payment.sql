CREATE TABLE IF NOT EXISTS `property_payment` (
  `payment_no` VARCHAR(64) NOT NULL COMMENT '支付单号',
  `owner_id` BIGINT NOT NULL COMMENT '业主ID',
  `bill_id` BIGINT NOT NULL COMMENT '账单ID',
  `bill_no` VARCHAR(64) NOT NULL COMMENT '账单编号',
  `company_id` BIGINT NOT NULL COMMENT '物业公司ID',
  `room_id` BIGINT NOT NULL COMMENT '房间ID',
  `amount` DECIMAL(12,2) NOT NULL COMMENT '实付金额',
  `channel` VARCHAR(16) NOT NULL COMMENT 'wechat/alipay',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已关闭',
  `pay_time` DATETIME NULL COMMENT '支付时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`payment_no`),
  KEY `idx_bill` (`bill_id`),
  KEY `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业账单支付单';

-- 2026-09-18 G81: 支持车位费缴费（fee_type 0=物业费 1=车位费）
ALTER TABLE property_payment ADD COLUMN fee_type INT NOT NULL DEFAULT 0 COMMENT '0=物业费 1=车位费';
