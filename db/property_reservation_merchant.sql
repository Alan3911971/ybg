-- ============================================================
-- G86 商家场地预约（双来源：业主/商家）+ 物业公司收费开关
-- 执行：mysql -uybgtc.com -psy7wGesYn5y22jsZ ybgtc.com < property_reservation_merchant.sql
-- ============================================================

-- 1. 物业公司：场地预约是否收费（0=免费 1=收费，后台配置）
ALTER TABLE property_company
    ADD COLUMN reservation_fee_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '场地预约是否收费：0免费 1收费' AFTER status;

-- 2. 预约表：来源类型 + 商家号 + 支付单号
ALTER TABLE property_reservation
    ADD COLUMN reserved_type TINYINT NOT NULL DEFAULT 1 COMMENT '预约来源：1业主 2商家' AFTER owner_id,
    ADD COLUMN merchant_no VARCHAR(32) NULL COMMENT '商家预约的商家编号' AFTER reserved_type,
    ADD COLUMN payment_no VARCHAR(64) NULL COMMENT '收费预约支付单号(RS前缀)' AFTER merchant_no;
