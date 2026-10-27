-- V19: 会员 CRM 增强（预约回复 / 生日提醒幂等 / AI 任务记录）
-- 1) 预约：补顾客留言与商家回复链路
ALTER TABLE member_appointment
  ADD COLUMN customer_msg VARCHAR(300) DEFAULT NULL COMMENT '顾客留言（预约时提交）' AFTER remark,
  ADD COLUMN reply_status INT NOT NULL DEFAULT 0 COMMENT '回复状态：0待回复 1已回复' AFTER status,
  ADD COLUMN reply_content VARCHAR(300) DEFAULT NULL COMMENT '商家回复内容' AFTER reply_status,
  ADD COLUMN reply_time DATETIME DEFAULT NULL COMMENT '回复时间' AFTER reply_content,
  ADD COLUMN reply_by VARCHAR(64) DEFAULT NULL COMMENT '回复人（商家登录账号/AI）' AFTER reply_time,
  ADD COLUMN reply_sent INT NOT NULL DEFAULT 0 COMMENT '回复已触达顾客：0否 1是' AFTER reply_by,
  ADD COLUMN reply_channel VARCHAR(32) DEFAULT NULL COMMENT '触达通道：user_message站内 / sms短信 / both' AFTER reply_sent;

-- 2) 生日提醒幂等表（按 商家+会员+年份+提前天数 去重，避免重复提醒/重复发券）
CREATE TABLE IF NOT EXISTS birthday_remind_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_no VARCHAR(32) NOT NULL COMMENT '商家编号',
  user_phone VARCHAR(20) NOT NULL COMMENT '会员手机号',
  remind_year INT NOT NULL COMMENT '提醒年份（YYYY）',
  days_ahead INT NOT NULL COMMENT '提前天数（7/3/1/0）',
  coupon_id BIGINT NULL COMMENT '已发放的生日关怀券ID',
  create_time DATETIME NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_birthday_remind (merchant_no, user_phone, remind_year, days_ahead),
  KEY idx_birthday_remind (merchant_no, remind_year, days_ahead)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生日提醒幂等记录';

-- 3) AI 任务记录（审计 + 降级留痕：记录走的是大模型还是本地规则）
CREATE TABLE IF NOT EXISTS ai_task_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  merchant_no VARCHAR(32) NOT NULL COMMENT '商家编号',
  biz_type VARCHAR(32) NOT NULL COMMENT '业务类型：visit_script/birthday_copy/churn_scan/appointment_reply',
  target_phone VARCHAR(20) DEFAULT NULL COMMENT '目标会员手机号',
  source VARCHAR(16) NOT NULL COMMENT '生成来源：llm大模型 / rule本地规则',
  model VARCHAR(64) DEFAULT NULL COMMENT '模型名（rule 时为 local-rule）',
  prompt TEXT COMMENT '提示词（截断 2000 字符）',
  result TEXT COMMENT '生成结果（截断 2000 字符）',
  cost_ms INT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  create_time DATETIME NOT NULL COMMENT '创建时间',
  KEY idx_ai_task (merchant_no, biz_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 任务记录';
