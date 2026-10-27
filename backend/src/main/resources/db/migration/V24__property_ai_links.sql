-- ============================================================================
-- V24: 物业 AI 新链落地（对应 property-ai.html 35 屏 + property-app.html 29 屏）
--   1. 业主群受理        property_chat_group / _member / _msg
--   2. 邻里互助 + 车位备案 property_mutual_post / property_parking_transfer
--   3. 违停曝光          property_parking_violation
--   4. 商家邀请两段式    property_merchant_referral
--   5. AI 主动规则配置   property_ai_rule
--   6. 补字段：property_workorder(source/source_ref/media)、merchant_property_binding(邀请四字段)
--
-- 生产库 flyway 关闭（-Dspring.flyway.enabled=false），需手动执行本文件。
-- 全文幂等：重复执行不报错、不覆盖既有数据。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 业主群
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS property_chat_group (
    group_id        BIGINT UNSIGNED AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL COMMENT '物业公司ID',
    community_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '小区ID',
    group_name      VARCHAR(128) NOT NULL COMMENT '群名称',
    group_type      TINYINT NOT NULL DEFAULT 1 COMMENT '1=全小区业主群 2=楼栋群 3=物业内部群',
    member_count    INT NOT NULL DEFAULT 0 COMMENT '成员数（冗余，成员表变动时重算）',
    intake_enabled  TINYINT NOT NULL DEFAULT 1 COMMENT '是否开启 AI 静默受理 1=开 0=关',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常 0=停用',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id),
    KEY idx_company (company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业主群';

CREATE TABLE IF NOT EXISTS property_chat_group_member (
    id            BIGINT UNSIGNED AUTO_INCREMENT,
    group_id      BIGINT UNSIGNED NOT NULL,
    owner_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '业主ID（成员非业主时为空）',
    room_id       BIGINT UNSIGNED DEFAULT NULL,
    room_no       VARCHAR(64) DEFAULT NULL COMMENT '冗余房号，如 5-2-601',
    member_name   VARCHAR(64) DEFAULT NULL,
    member_phone  VARCHAR(20) DEFAULT NULL,
    member_role   TINYINT NOT NULL DEFAULT 1 COMMENT '1=业主 2=家属 3=物业员工 4=AI机器人',
    status        TINYINT NOT NULL DEFAULT 1 COMMENT '1=在群 0=退群',
    join_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_owner (group_id, owner_id),
    KEY idx_group (group_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业主群成员';

CREATE TABLE IF NOT EXISTS property_group_msg (
    msg_id           BIGINT UNSIGNED AUTO_INCREMENT,
    group_id         BIGINT UNSIGNED NOT NULL,
    company_id       BIGINT UNSIGNED NOT NULL,
    sender_owner_id  BIGINT UNSIGNED DEFAULT NULL,
    sender_name      VARCHAR(64) DEFAULT NULL,
    room_no          VARCHAR(64) DEFAULT NULL,
    content          TEXT NOT NULL COMMENT '群内原话',
    media            JSON DEFAULT NULL COMMENT '图片/视频 [{type,url}]',
    msg_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_demand        TINYINT NOT NULL DEFAULT 0 COMMENT '1=被识别为一条需求',
    demand_kind      VARCHAR(32) DEFAULT NULL COMMENT 'repair/complaint/help/bill_ask/other',
    intent_label     VARCHAR(64) DEFAULT NULL COMMENT '如 报修·电梯',
    confidence       DECIMAL(4,3) DEFAULT NULL COMMENT '识别置信度 0-1',
    wo_id            BIGINT UNSIGNED DEFAULT NULL COMMENT '生成的工单ID；并单时指向原单',
    wo_no            VARCHAR(64) DEFAULT NULL,
    receipt_status   TINYINT NOT NULL DEFAULT 0 COMMENT '0=无需回执 1=已受理 2=待人工判断 3=已忽略 4=已并单',
    receipt_msg      VARCHAR(500) DEFAULT NULL COMMENT '群里发出的那条静默回执',
    receipt_time     DATETIME DEFAULT NULL,
    handle_admin_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '人工判断人',
    handle_time      DATETIME DEFAULT NULL,
    handle_note      VARCHAR(255) DEFAULT NULL,
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (msg_id),
    KEY idx_group_time (group_id, msg_time),
    KEY idx_demand (company_id, is_demand, receipt_status),
    KEY idx_wo (wo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业主群消息+受理状态';

-- ---------------------------------------------------------------------------
-- 2. 邻里互助（车位/找师傅/二手/拼车/照看）+ 车位转让备案
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS property_mutual_post (
    post_id        BIGINT UNSIGNED AUTO_INCREMENT,
    company_id     BIGINT UNSIGNED NOT NULL,
    community_id   BIGINT UNSIGNED DEFAULT NULL,
    post_type      TINYINT NOT NULL COMMENT '1=车位出租 2=车位求租 3=找师傅 4=二手 5=拼车 6=照看',
    title          VARCHAR(200) NOT NULL,
    content        TEXT DEFAULT NULL,
    owner_id       BIGINT UNSIGNED NOT NULL COMMENT '发布业主',
    room_no        VARCHAR(64) DEFAULT NULL,
    contact        VARCHAR(64) DEFAULT NULL,
    price_desc     VARCHAR(64) DEFAULT NULL COMMENT '如 ¥280/月、¥80 起、¥150',
    status         TINYINT NOT NULL DEFAULT 0 COMMENT '0=在挂 1=已成交 2=已下架 3=已过期',
    response_count INT NOT NULL DEFAULT 0,
    deal_owner_id  BIGINT UNSIGNED DEFAULT NULL,
    deal_time      DATETIME DEFAULT NULL,
    expire_time    DATETIME DEFAULT NULL,
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id),
    KEY idx_company_type (company_id, post_type, status),
    KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邻里互助挂牌';

CREATE TABLE IF NOT EXISTS property_parking_transfer (
    transfer_id     BIGINT UNSIGNED AUTO_INCREMENT,
    company_id      BIGINT UNSIGNED NOT NULL,
    post_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '来源互助挂牌',
    car_plate       VARCHAR(20) NOT NULL COMMENT '车牌（全牌，仅后台可见）',
    space_no        VARCHAR(32) DEFAULT NULL COMMENT '车位号，如 B2-118',
    from_owner_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '原租户',
    from_room_no    VARCHAR(64) DEFAULT NULL,
    to_owner_id     BIGINT UNSIGNED DEFAULT NULL COMMENT '新租户',
    to_room_no      VARCHAR(64) DEFAULT NULL,
    step_confirm    TINYINT NOT NULL DEFAULT 0 COMMENT '① 双方确认',
    step_account    TINYINT NOT NULL DEFAULT 0 COMMENT '② 月租账户变更',
    step_whitelist  TINYINT NOT NULL DEFAULT 0 COMMENT '③ 道闸白名单生效',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0=进行中 1=已备案 2=已取消',
    fee_month       DECIMAL(10,2) DEFAULT NULL COMMENT '月租（含管理费）',
    confirm_time    DATETIME DEFAULT NULL,
    account_time    DATETIME DEFAULT NULL,
    whitelist_time  DATETIME DEFAULT NULL,
    operator_id     BIGINT UNSIGNED DEFAULT NULL,
    remark          VARCHAR(255) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (transfer_id),
    KEY idx_company_status (company_id, status),
    KEY idx_plate (car_plate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车位转让备案（三步）';

-- ---------------------------------------------------------------------------
-- 3. 违停曝光
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS property_parking_violation (
    vio_id           BIGINT UNSIGNED AUTO_INCREMENT,
    company_id       BIGINT UNSIGNED NOT NULL,
    community_id     BIGINT UNSIGNED DEFAULT NULL,
    location         VARCHAR(128) NOT NULL COMMENT '位置描述',
    car_plate        VARCHAR(20) DEFAULT NULL COMMENT '识别车牌（全牌）',
    plate_masked     VARCHAR(20) DEFAULT NULL COMMENT '对外打星车牌',
    submit_owner_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '提交业主',
    submit_room_no   VARCHAR(64) DEFAULT NULL,
    media            JSON DEFAULT NULL COMMENT '照片/视频 [{type,url}]',
    shoot_time       DATETIME DEFAULT NULL COMMENT '拍摄时间（EXIF 或前端传入）',
    recognize_result TINYINT DEFAULT NULL COMMENT '1=登记车 2=访客 3=无登记',
    vehicle_id       BIGINT UNSIGNED DEFAULT NULL,
    owner_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '车主业主ID',
    action           TINYINT DEFAULT NULL COMMENT '1=通知车主 2=通知邀请人 3=转贴单 4=曝光',
    notify_time      DATETIME DEFAULT NULL,
    move_away_time   DATETIME DEFAULT NULL,
    minutes_used     INT DEFAULT NULL COMMENT '挪走耗时（分钟）',
    status           TINYINT NOT NULL DEFAULT 0 COMMENT '0=待处置 1=已挪走 2=待审曝光 3=已上曝光 4=误报 5=已关单',
    exposure_draft   TEXT DEFAULT NULL COMMENT '曝光稿草稿',
    exposure_pub_time DATETIME DEFAULT NULL,
    handler_id       BIGINT UNSIGNED DEFAULT NULL,
    handle_note      VARCHAR(255) DEFAULT NULL,
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (vio_id),
    KEY idx_company_status (company_id, status),
    KEY idx_plate_time (car_plate, create_time),
    KEY idx_submit (submit_owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='违停曝光';

-- ---------------------------------------------------------------------------
-- 4. 商家邀请（两段式佣金）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS property_merchant_referral (
    referral_id        BIGINT UNSIGNED AUTO_INCREMENT,
    company_id         BIGINT UNSIGNED NOT NULL,
    code               VARCHAR(40) NOT NULL COMMENT '邀请码，一人一码',
    owner_id           BIGINT UNSIGNED NOT NULL COMMENT '邀请人',
    room_no            VARCHAR(64) DEFAULT NULL,
    category           VARCHAR(32) DEFAULT NULL COMMENT '限定类目（生鲜/餐饮/家政…）',
    status             TINYINT NOT NULL DEFAULT 0 COMMENT '0=已出码 1=已扫码 2=已过审 3=已过期 4=已作废',
    expire_time        DATETIME NOT NULL,
    merchant_no        VARCHAR(64) DEFAULT NULL COMMENT '注册生成的商家编号',
    merchant_name      VARCHAR(128) DEFAULT NULL,
    binding_id         BIGINT UNSIGNED DEFAULT NULL COMMENT 'merchant_property_binding.id',
    license_url        VARCHAR(500) DEFAULT NULL COMMENT '执照图片',
    scan_time          DATETIME DEFAULT NULL,
    audit_time         DATETIME DEFAULT NULL,
    audit_admin_id     BIGINT UNSIGNED DEFAULT NULL,
    audit_note         VARCHAR(255) DEFAULT NULL,
    reward30_status    TINYINT NOT NULL DEFAULT 0 COMMENT '0=未发 1=已发',
    reward30_time      DATETIME DEFAULT NULL,
    reward70_status    TINYINT NOT NULL DEFAULT 0 COMMENT '0=待条件 1=已发 2=不满足',
    reward70_time      DATETIME DEFAULT NULL,
    first_order_amount DECIMAL(10,2) DEFAULT NULL,
    first_order_time   DATETIME DEFAULT NULL,
    refund_deadline    DATETIME DEFAULT NULL COMMENT '首单+7天，期满无退款才发 ¥70',
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (referral_id),
    UNIQUE KEY uk_code (code),
    KEY idx_company_status (company_id, status),
    KEY idx_owner (owner_id),
    KEY idx_merchant (merchant_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家邀请码与两段式佣金';

-- ---------------------------------------------------------------------------
-- 5. AI 主动规则配置（能力全景/主动规则页数据源）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS property_ai_rule (
    rule_id        BIGINT UNSIGNED AUTO_INCREMENT,
    company_id     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=全局默认规则',
    event_code     VARCHAR(48) NOT NULL COMMENT '事件编码',
    event_name     VARCHAR(64) NOT NULL,
    trigger_desc   VARCHAR(200) DEFAULT NULL COMMENT '触发条件',
    action_desc    VARCHAR(500) DEFAULT NULL COMMENT 'AI 动作',
    authorize_level TINYINT NOT NULL DEFAULT 1 COMMENT '1=L1自动 2=L2待确认 3=L3只能你做',
    budget_desc    VARCHAR(200) DEFAULT NULL COMMENT '打扰预算',
    week_count     INT NOT NULL DEFAULT 0 COMMENT '本周触发次数',
    status         TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=暂停',
    sort_no        INT NOT NULL DEFAULT 0,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_company_event (company_id, event_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 主动规则';

-- 13 条默认规则（幂等：靠 uk_company_event）
INSERT IGNORE INTO property_ai_rule
    (company_id, event_code, event_name, trigger_desc, action_desc, authorize_level, budget_desc, week_count, status, sort_no) VALUES
(0, 'wo_timeout',      '工单超时',   '超 30 分钟未接单', '按技能+距离+负荷+返修率派单；改人改时间由你', 1, '只私信维修工，不打扰业主', 42, 1, 10),
(0, 'deduct_settle',   '余额抵扣',   '每日 02:00 分账结算到账', '先抵物业费欠款，不抵商城消费', 1, '每户 1 条，账单日合并发', 93, 1, 20),
(0, 'bill_dunning',    '催缴',       '逾期 ≥ 7 天', '分三档起草：差额 / 全欠 / 争议不催', 2, '每户每周 ≤1 条，21:00-08:00 不发', 265, 1, 30),
(0, 'device_offline',  '设备离线',   '心跳中断 ≥ 10 分钟', '起草报修单+停梯窗口+通知稿；不猜硬件', 2, '只发维保，不发业主', 3, 1, 40),
(0, 'inspect_miss',    '巡检漏检',   '16:00 计划未打点', '自动转工单；同日漏 2 次才判路线不合理', 1, '不打扰', 2, 1, 50),
(0, 'group_intake',    '业主群受理', '群内出现报修/投诉/求助', '自动填单+静默回执；同题 24h 并单', 1, '群里只回 1 条结果，过程不进群', 70, 1, 60),
(0, 'parking_violate', '违停',       '随手拍识别出车牌', '登记车自动通知 6 分钟挪走；无登记起草贴单+曝光稿', 1, '同车牌 24h 算 1 条', 47, 1, 70),
(0, 'space_transfer',  '车位备案',   '互助挂牌成交', '双方确认→改月租账户→白名单次日生效', 2, '不打扰', 9, 1, 80),
(0, 'referral_reward', '邀请佣金',   '商家过审 / 首单满 ¥100 且 7 天无退款', '¥30 秒入邀请人钱包；¥70 满足条件补发', 1, '到账只发 1 条，不催', 5, 1, 90),
(0, 'bill_reduce',     '减免·退款',  '金额超角色额度', '只出证据包（账单+抵扣流水+历史），不改金额', 3, '—', 4, 1, 100),
(0, 'face_audit',      '人脸审批',   '抓拍相似度 90-95%', '预筛排序，可疑留人工；>95% 可批量通过', 2, '批量通过后通知业主 1 条', 12, 1, 110),
(0, 'elevator_notice', '停梯公告',   '停梯窗口确定', '生成公告+定向发受影响楼栋', 2, '仅受影响楼栋，每事件 1 条', 2, 1, 120),
(0, 'price_change',    '调价·涨费',  '涉及费率变更', '只给测算与同小区对比，不落库', 3, '—', 0, 0, 130);

-- ---------------------------------------------------------------------------
-- 6. 补字段（幂等）
-- ---------------------------------------------------------------------------
SET @dbname = DATABASE();

-- 6.0 property_chat_group_member：补 company_id（按公司统计群成员）
SET @tbl = 'property_chat_group_member';
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'company_id';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE property_chat_group_member ADD COLUMN company_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''冗余公司ID''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6.1 property_workorder：来源与视频
SET @tbl = 'property_workorder';

SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'source';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE property_workorder ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT ''owner'' COMMENT ''来源 owner/app/group/patrol/exposure/phone''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'source_ref';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE property_workorder ADD COLUMN source_ref VARCHAR(64) DEFAULT NULL COMMENT ''来源单据ID：群消息ID/违停ID''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'media';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE property_workorder ADD COLUMN media JSON DEFAULT NULL COMMENT ''视频等媒体 [{type,url,cover}]，图片仍走 images''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6.2 merchant_property_binding：邀请关系四字段
SET @tbl = 'merchant_property_binding';

SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'referrer_owner_id';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE merchant_property_binding ADD COLUMN referrer_owner_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''邀请人业主ID''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'invite_code';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE merchant_property_binding ADD COLUMN invite_code VARCHAR(40) DEFAULT NULL COMMENT ''邀请码''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'invite_expire_at';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE merchant_property_binding ADD COLUMN invite_expire_at DATETIME DEFAULT NULL COMMENT ''邀请码过期时间''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
WHERE table_schema = @dbname AND table_name = @tbl AND column_name = 'referral_reward_status';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE merchant_property_binding ADD COLUMN referral_reward_status VARCHAR(16) DEFAULT NULL COMMENT ''NONE/R30/R70''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
