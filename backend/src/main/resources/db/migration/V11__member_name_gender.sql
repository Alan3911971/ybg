-- V11: 会员资料加姓名/性别
ALTER TABLE member_profile
  ADD COLUMN name VARCHAR(32) DEFAULT NULL COMMENT '会员姓名' AFTER user_phone,
  ADD COLUMN gender VARCHAR(8) DEFAULT NULL COMMENT '性别（男/女）' AFTER name;
