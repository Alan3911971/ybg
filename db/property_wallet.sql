-- 物业钱包（业主在物业公司维度的余额，中奖余额入账 / 缴费抵扣）
CREATE TABLE IF NOT EXISTS property_wallet (
  wallet_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  company_id BIGINT NOT NULL,
  balance DECIMAL(12,2) DEFAULT 0.00,
  create_time DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
  update_time DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_owner_company (owner_id, company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
