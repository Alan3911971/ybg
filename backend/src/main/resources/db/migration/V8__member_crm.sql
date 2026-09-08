-- V8: 会员管理 CRM（喜好/家人喜好/回访/预约）
CREATE TABLE IF NOT EXISTS member_profile (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_no VARCHAR(32) NOT NULL COMMENT '商家编号',
  user_phone VARCHAR(20) NOT NULL COMMENT '会员手机号',
  customer_pref VARCHAR(1000) DEFAULT NULL COMMENT '客户喜好',
  family_pref VARCHAR(1000) DEFAULT NULL COMMENT '家人喜好',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_profile_mno_phone (merchant_no, user_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员资料(喜好/家人喜好)';

CREATE TABLE IF NOT EXISTS member_visit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_no VARCHAR(32) NOT NULL COMMENT '商家编号',
  user_phone VARCHAR(20) NOT NULL COMMENT '会员手机号',
  content VARCHAR(500) NOT NULL COMMENT '回访内容',
  result VARCHAR(200) DEFAULT NULL COMMENT '回访结果',
  visit_time DATETIME DEFAULT NULL COMMENT '回访时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_visit_mno_phone (merchant_no, user_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员回访记录';

CREATE TABLE IF NOT EXISTS member_appointment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_no VARCHAR(32) NOT NULL COMMENT '商家编号',
  user_phone VARCHAR(20) NOT NULL COMMENT '会员手机号',
  appt_time DATETIME NOT NULL COMMENT '预约时间',
  service_item VARCHAR(100) DEFAULT NULL COMMENT '服务项目',
  remark VARCHAR(200) DEFAULT NULL COMMENT '备注',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待服务 1已完成 2已取消',
  remind_minutes INT NOT NULL DEFAULT 30 COMMENT '提前提醒分钟数',
  remind_sent TINYINT NOT NULL DEFAULT 0 COMMENT '提醒已发送 0否 1是',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_appt_mno_status (merchant_no, status, appt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员预约';
