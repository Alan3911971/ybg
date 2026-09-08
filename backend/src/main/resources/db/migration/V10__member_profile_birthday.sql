-- V10: 会员资料加生日/录入员工
ALTER TABLE member_profile
  ADD COLUMN birthday VARCHAR(10) DEFAULT NULL COMMENT '生日（MM-dd，如 09-20）' AFTER family_pref,
  ADD COLUMN editor VARCHAR(64) DEFAULT NULL COMMENT '录入员工（商家登录账号）' AFTER remark;
