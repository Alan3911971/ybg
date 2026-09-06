-- V5: 奖品池添加过期时间列
ALTER TABLE box_prize_pool ADD COLUMN expire_time DATETIME DEFAULT NULL COMMENT '公共池截止时间（null=不限）过期后自动下架' AFTER update_time;
