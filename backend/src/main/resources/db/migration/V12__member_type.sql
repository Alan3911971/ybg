-- V12: 会员类型
ALTER TABLE member_profile
  ADD COLUMN member_type VARCHAR(16) DEFAULT '普通会员' COMMENT '会员类型（普通会员/VIP会员/黑金会员）' AFTER gender;
