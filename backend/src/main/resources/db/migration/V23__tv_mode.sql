-- 大屏显示模式：call=叫号屏（默认） / activity=盲盒活动屏（无叫号，左区改盲盒主视觉+大二维码）
-- 生产库 flyway 关闭（启动加 -Dspring.flyway.enabled=false），需手动执行本文件。
ALTER TABLE merchant_queue_config ADD COLUMN tv_mode VARCHAR(16) NOT NULL DEFAULT 'call';
