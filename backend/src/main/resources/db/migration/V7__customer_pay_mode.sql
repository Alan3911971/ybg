-- V7: 客户支付模式（静态收款码 / API支付）
ALTER TABLE `merchant` ADD COLUMN `pay_mode` INT NOT NULL DEFAULT 0 COMMENT '客户支付模式 0=静态收款码(默认) 1=API支付(微信/支付宝)' AFTER `receive_qr_status`;

ALTER TABLE `offline_order` ADD COLUMN `pay_channel` VARCHAR(32) DEFAULT NULL COMMENT '支付渠道 wechat/alipay' AFTER `pay_time`;
