-- V9: 会员礼品赠送
CREATE TABLE IF NOT EXISTS member_gift (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_no VARCHAR(32) NOT NULL COMMENT '商家编号',
  user_phone VARCHAR(20) NOT NULL COMMENT '会员手机号',
  goods_id BIGINT NULL COMMENT '商品ID（关联ibigou_goods）',
  goods_name VARCHAR(128) NOT NULL COMMENT '商品名称（冗余快照）',
  quantity INT NOT NULL DEFAULT 1 COMMENT '赠送数量',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注（如：生日礼物）',
  source VARCHAR(20) NOT NULL DEFAULT 'gift' COMMENT '来源：gift独立赠送 / visit回访附带',
  create_time DATETIME NOT NULL COMMENT '赠送时间',
  KEY idx_member_gift (merchant_no, user_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员礼品赠送记录';
