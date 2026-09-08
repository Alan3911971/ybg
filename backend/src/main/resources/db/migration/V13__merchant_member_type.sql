-- V13: 会员类型配置（商家自定义）
CREATE TABLE IF NOT EXISTS merchant_member_type (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_no VARCHAR(32) NOT NULL COMMENT '商家编号',
  type_name VARCHAR(16) NOT NULL COMMENT '类型名称',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  create_time DATETIME NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_mt (merchant_no, type_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家自定义会员类型';

INSERT IGNORE INTO merchant_member_type (merchant_no, type_name, sort_order, create_time)
SELECT merchant_no, t.n, t.s, NOW() FROM (
  SELECT DISTINCT merchant_no FROM member_profile
) mp CROSS JOIN (
  SELECT '普通会员' n, 0 s UNION SELECT 'VIP会员', 1 UNION SELECT '黑金会员', 2
) t;
