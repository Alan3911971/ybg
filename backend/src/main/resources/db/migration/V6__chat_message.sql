-- V6: 在线客服聊天消息表（顾客与商家双向沟通）
CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_phone VARCHAR(20) NOT NULL COMMENT '顾客手机号',
  merchant_no VARCHAR(32) NOT NULL COMMENT '归属商家编号',
  from_role VARCHAR(16) NOT NULL COMMENT 'customer/merchant',
  content VARCHAR(1000) NOT NULL,
  is_read INT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  create_time DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_phone_mno (user_phone, merchant_no),
  KEY idx_chat_mno_role (merchant_no, from_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线客服聊天';
