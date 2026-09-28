-- 物业开奖配置（复刻商家奖品池，company 维度，含渠道分组）
CREATE TABLE IF NOT EXISTS property_prize_pool (
  prize_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id BIGINT NOT NULL COMMENT '物业公司',
  prize_type INT DEFAULT 0 COMMENT '0普通商品 1折扣券 2立减券 3普通余额 4团购免单余额',
  prize_value DECIMAL(10,2) DEFAULT 0.00 COMMENT '面值/额度',
  weight INT DEFAULT 1 COMMENT '权重（越大越容易中）',
  enabled INT DEFAULT 1 COMMENT '1启用 0停用',
  remark VARCHAR(128) DEFAULT '' COMMENT '奖品名称',
  pool_type INT DEFAULT 0 COMMENT '0本店奖品池 1公共奖品池',
  channel INT DEFAULT 0 COMMENT '0通用 1美团 2团购 3抖音',
  create_time DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  update_time DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_company (company_id, channel, pool_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
