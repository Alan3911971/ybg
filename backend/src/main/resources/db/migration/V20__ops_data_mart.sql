-- =============================================================================
-- V20 —— 运营数据中台（全渠道大屏 + AI 执行日志留痕）
--
-- 对应设计稿：
--   _redesign_v3/merchant-screen.html        全渠道运营数据大屏 1920×1080
--   _redesign_v3/merchant-ai-log.html        AI 执行日志（可回放 / 6 步留痕）
--   _redesign_v3/merchant-web.html#v-log     网页后台「执行日志」视图
--
-- 口径文档：docs/运营数据口径-V1.md
-- ETL 脚本：db/etl/sp_ops_rebuild.sql（不进 Flyway，需人工 dry-run 后单独执行）
--
-- 四条设计原则：
--   1. 只新增表、只新增可空列，不改任何现有列语义 —— 老代码零影响
--   2. 日汇总表按 (商家, 日期, 维度) 唯一，重跑幂等
--   3. 渠道归因落在 offline_order.channel_code；NULL 一律归 walkin 自然到店
--   4. 金额 DECIMAL(12,2)，比率 DECIMAL(5,4)（0.3250 = 32.50%）
--
-- 为什么不把 ETL 放这里：ETL 依赖商品成本、券成本归属等业务口径，首次上线
-- 需要在测试库 dry-run 对账；Flyway 一旦失败会阻塞整个应用启动，风险不对等。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 0) 幂等补列：给核心交易表加「渠道归因」，不动默认行为
-- -----------------------------------------------------------------------------
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_v20_add_cols$$
CREATE PROCEDURE sp_v20_add_cols()
BEGIN
  -- offline_order：这笔订单从哪个渠道来的（NULL = 未归因，统计时归 walkin）
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offline_order'
                   AND COLUMN_NAME = 'channel_code') THEN
    ALTER TABLE offline_order
      ADD COLUMN channel_code VARCHAR(32) DEFAULT NULL
        COMMENT '归因渠道 douyin/meituan/miniapp/qr_instore/channels/private/walkin/referral/other（NULL=未归因，统计归 walkin）'
        AFTER merchant_no,
      ADD COLUMN staff_no VARCHAR(32) DEFAULT NULL
        COMMENT '服务员工号，用于人效与提成' AFTER channel_code;
  END IF;

  -- user_coupon：这张券是哪个渠道领的（可能与核销渠道不同，用于渠道转化分析）
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_coupon'
                   AND COLUMN_NAME = 'acquire_channel_code') THEN
    ALTER TABLE user_coupon
      ADD COLUMN acquire_channel_code VARCHAR(32) DEFAULT NULL
        COMMENT '领券渠道，NULL=未打点' AFTER source_merchant_no,
      ADD COLUMN face_amount DECIMAL(10,2) DEFAULT NULL
        COMMENT '券面额（prize_type=立减时为优惠值；团购券为售价），用于让利成本核算' AFTER prize_value;
  END IF;

  -- channel_code 上补索引，ETL 按渠道聚合要用
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offline_order'
                   AND INDEX_NAME = 'idx_offline_channel') THEN
    ALTER TABLE offline_order
      ADD KEY idx_offline_channel (merchant_no, channel_code, pay_time);
  END IF;
END$$
DELIMITER ;

CALL sp_v20_add_cols();
DROP PROCEDURE IF EXISTS sp_v20_add_cols;


-- -----------------------------------------------------------------------------
-- 1) 商家运营参数：目标 / 毛利率 / 佣金 / 券成本比
--    大屏「月目标 39,000」「毛利率目标 35.5%」「佣金 2.5%」都从这里取
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS merchant_ops_config (
  merchant_no             VARCHAR(64)   NOT NULL COMMENT '商家编号',
  -- 注意这两个毛利率不是一回事，混用会导致大屏数字打架：
  --   goods_margin_rate  =(净实收-商品成本)/净实收      → 只用来反算商品成本
  --   gross_margin_target= 毛利/净实收（已扣券成本+佣金）→ 大屏「低于目标」对比用
  -- 两者关系：最终毛利率 = goods_margin_rate - (券成本+佣金)/净实收
  gross_margin_target     DECIMAL(5,4)  NOT NULL DEFAULT 0.3550 COMMENT '最终毛利率目标（已扣券成本与佣金），低于则大屏飘红',
  goods_margin_rate       DECIMAL(5,4)  NOT NULL DEFAULT 0.4000 COMMENT '商品毛利率，未录商品成本时用它反算成本；不是最终毛利率',
  coupon_cost_ratio       DECIMAL(5,4)  NOT NULL DEFAULT 0.4200 COMMENT '券成本占券面额比例，例 0.42 = 42%',
  commission_rate_default DECIMAL(5,4)  NOT NULL DEFAULT 0.0250 COMMENT '平台渠道默认佣金率',
  month_target_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '月营收目标；0 = 按近 28 日日均×当月天数自动推',
  day_target_amount       DECIMAL(12,2) DEFAULT NULL COMMENT '日目标；空则由 月目标/当月天数 推',
  member_target_month     INT           NOT NULL DEFAULT 0 COMMENT '本月新增会员目标',
  loss_rate_target        DECIMAL(5,4)  NOT NULL DEFAULT 0.0600 COMMENT '损耗率目标',
  biz_open_time           TIME          NOT NULL DEFAULT '09:00:00' COMMENT '营业开始，用于算时段空档',
  biz_close_time          TIME          NOT NULL DEFAULT '22:00:00' COMMENT '营业结束',
  create_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (merchant_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家运营参数（目标/毛利率/佣金/券成本口径）';


-- -----------------------------------------------------------------------------
-- 2) 渠道字典（平台级）：决定「自有 / 平台 / 线下」三分，以及佣金率
--    大屏底部「自有 62.0% 不付佣金 · 平台 38.0% 佣金 2.5%」由 channel_type 决定
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ops_channel_dict (
  channel_code    VARCHAR(32) NOT NULL COMMENT '渠道码',
  channel_name    VARCHAR(32) NOT NULL COMMENT '展示名',
  channel_type    VARCHAR(16) NOT NULL DEFAULT 'platform'
                  COMMENT 'own=自有不付佣金 / platform=平台付佣金 / offline=线下自然',
  commission_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000 COMMENT '默认佣金率',
  color_hex       VARCHAR(8)  DEFAULT NULL COMMENT '大屏配色，空则前端调色板轮转',
  sort_no         INT         NOT NULL DEFAULT 100 COMMENT '图例排序',
  status          TINYINT     NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  PRIMARY KEY (channel_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道字典（平台级）';

INSERT IGNORE INTO ops_channel_dict
  (channel_code, channel_name, channel_type, commission_rate, color_hex, sort_no) VALUES
  ('douyin',      '抖音团购',  'platform', 0.0250, '#ff6b5e', 10),
  ('meituan',     '美团外卖',  'platform', 0.0250, '#ffc53d', 20),
  ('miniapp',     '小程序自提','own',      0.0000, '#35d6a4', 30),
  ('qr_instore',  '到店扫码',  'own',      0.0000, '#4d9fff', 40),
  ('channels',    '视频号',    'platform', 0.0250, '#9b8bfb', 50),
  ('private',     '私域社群',  'own',      0.0000, '#3ee0ad', 60),
  ('walkin',      '自然到店',  'offline',  0.0000, '#8ba3b8', 70),
  ('referral',    '会员转介',  'own',      0.0000, '#7dd3fc', 80),
  ('xiaohongshu', '小红书',    'platform', 0.0250, '#ff938a', 90),
  ('other',       '其他',      'offline',  0.0000, '#5b6b7c', 99);


-- -----------------------------------------------------------------------------
-- 3) 商家渠道接入状态：大屏「已接通 5 · 待授权 2 · 仅素材 2」
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS merchant_channel_bind (
  merchant_no      VARCHAR(64) NOT NULL COMMENT '商家编号',
  channel_code     VARCHAR(32) NOT NULL COMMENT '渠道码',
  access_status    TINYINT     NOT NULL DEFAULT 0
                   COMMENT '0未接入 1已接通 2待授权 3仅素材（能发内容不能挂单）4已断开',
  account_name     VARCHAR(64) DEFAULT NULL COMMENT '渠道账号名',
  auth_expire_date DATE        DEFAULT NULL COMMENT '授权到期日，用于提前 7 天预警',
  last_sync_time   DATETIME    DEFAULT NULL COMMENT '最后一次成功同步时间',
  remark           VARCHAR(200) DEFAULT NULL,
  update_time      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (merchant_no, channel_code),
  KEY idx_bind_status (merchant_no, access_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家渠道接入状态';


-- -----------------------------------------------------------------------------
-- 4) 渠道日汇总（大屏主数据源：KPI / 14 日柱线 / 渠道环 / 佣金 / 毛利）
--    重跑幂等：UNIQUE(商家, 日期, 渠道)，ETL 用 src='etl' 与手工修正行隔离
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS channel_daily (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  merchant_no         VARCHAR(64)  NOT NULL COMMENT '商家编号',
  stat_date           DATE         NOT NULL COMMENT '统计日（自然日，按 Asia/Shanghai）',
  channel_code        VARCHAR(32)  NOT NULL COMMENT '渠道码，见 ops_channel_dict',

  impression_cnt      INT          NOT NULL DEFAULT 0 COMMENT '曝光次数',
  visit_cnt           INT          NOT NULL DEFAULT 0 COMMENT '进店/点开人数',
  lead_cnt            INT          NOT NULL DEFAULT 0 COMMENT '领券/加购数',
  order_cnt           INT          NOT NULL DEFAULT 0 COMMENT '下单数',
  pay_cnt             INT          NOT NULL DEFAULT 0 COMMENT '支付/核销单数',
  refund_cnt          INT          NOT NULL DEFAULT 0 COMMENT '退款单数',

  gmv                 DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '成交额（原价合计）',
  pay_amount          DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实收（含券抵扣后）',
  refund_amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '退款金额',
  net_amount          DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '净实收 = 实收 - 退款',

  discount_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '让利成本合计（券面额 + 满减），大屏「让利成本」',
  coupon_issue_cnt    INT          NOT NULL DEFAULT 0 COMMENT '发出券张数（大屏「本周发 47 张」）',
  coupon_verify_cnt   INT          NOT NULL DEFAULT 0 COMMENT '核销券张数（大屏「核销 43 张 · 92%」）',
  coupon_face_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '券面额合计',
  coupon_cost_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '我方承担的券成本 = 面额 × coupon_cost_ratio',
  commission_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '平台佣金',
  cost_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '商品成本（含损耗分摊）',
  gross_profit        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '毛利 = 净实收 - 券成本 - 佣金 - 商品成本',

  new_member_cnt      INT          NOT NULL DEFAULT 0 COMMENT '带来新会员数',
  member_pay_cnt      INT          NOT NULL DEFAULT 0 COMMENT '会员订单数',
  cost_is_estimated   TINYINT      NOT NULL DEFAULT 0 COMMENT '1=商品成本按兜底毛利率估算，页面须标注',

  src                 VARCHAR(16)  NOT NULL DEFAULT 'etl' COMMENT 'etl=自动汇总 manual=人工修正 api=平台接口',
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uk_channel_daily (merchant_no, stat_date, channel_code),
  KEY idx_channel_daily_date (merchant_no, stat_date),
  KEY idx_channel_daily_amt (merchant_no, stat_date, net_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道日汇总（大屏主表）';


-- -----------------------------------------------------------------------------
-- 5) 漏斗日汇总（大屏「曝光 → 复购」五步）
--    prev_cnt / conv_rate 冗余存，页面直接读，避免每次窗口计算
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS funnel_daily (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  merchant_no  VARCHAR(64) NOT NULL COMMENT '商家编号',
  stat_date    DATE        NOT NULL COMMENT '统计日',
  channel_code VARCHAR(32) NOT NULL DEFAULT 'all' COMMENT 'all=全渠道；也可按单渠道拆',
  step_code    VARCHAR(24) NOT NULL
               COMMENT 'exposure=内容曝光 visit=进店点开 lead=领券加购 verify=到店核销 repurchase=二次到店',
  step_no      TINYINT     NOT NULL COMMENT '步骤序号 1..5',
  step_name    VARCHAR(32) NOT NULL COMMENT '展示名',
  unit         VARCHAR(8)  NOT NULL DEFAULT '人' COMMENT '次/人/张',
  user_cnt     INT         NOT NULL DEFAULT 0 COMMENT '本步数量',
  prev_cnt     INT         NOT NULL DEFAULT 0 COMMENT '上一步数量，用于算转化率',
  conv_rate    DECIMAL(7,4) DEFAULT NULL COMMENT '本步/上一步，0.0670 = 6.70%',
  src          VARCHAR(16) NOT NULL DEFAULT 'etl',
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_funnel_daily (merchant_no, stat_date, channel_code, step_code),
  KEY idx_funnel_date (merchant_no, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转化漏斗日汇总';


-- -----------------------------------------------------------------------------
-- 6) 门店级通用指标日表（不属渠道维度的指标都放这，避免加一个指标建一张表）
--    预置：复购率 / 损耗率 / 连带率 / 叫号组数 / 平均等待秒 / 自提准时率
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ops_metric_daily (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  merchant_no  VARCHAR(64)  NOT NULL COMMENT '商家编号',
  stat_date    DATE         NOT NULL COMMENT '统计日',
  metric_code  VARCHAR(48)  NOT NULL
               COMMENT 'repurchase_rate_30d/loss_rate/attach_rate/queue_total/queue_wait_avg_sec/pickup_ontime_rate',
  metric_name  VARCHAR(48)  NOT NULL COMMENT '展示名',
  metric_value DECIMAL(14,4) NOT NULL DEFAULT 0.0000 COMMENT '指标值',
  unit         VARCHAR(8)   NOT NULL DEFAULT '' COMMENT '%/元/秒/组',
  src          VARCHAR(16)  NOT NULL DEFAULT 'etl',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_metric_daily (merchant_no, stat_date, metric_code),
  KEY idx_metric_date (merchant_no, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店级指标日表（复购/损耗/连带/排队）';


-- -----------------------------------------------------------------------------
-- 7) AI 动作主体（日志页列表 + 效果倍数 + 没成功清单）
--
--    关键：把「执行状态」和「结果判定」拆成两个字段。
--    设计稿四个数是 17 = 12 成功 + 3 没成功 + 2 等你确认，
--    若只用一个 status 枚举，会出现「被驳回后修好」到底算不算成功的争议。
--    run_status=1 待商家确认 → 「等你点」；result=1 → 「成功」；result IN(2,3) → 「没成功」。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_action (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  action_no           VARCHAR(40)  NOT NULL COMMENT '动作号 AI-20261019-001，对外展示 + 幂等键',
  merchant_no         VARCHAR(64)  NOT NULL COMMENT '商家编号',
  title               VARCHAR(128) NOT NULL COMMENT '给老板看的标题，如「抖音 9.9 元试吃券」',
  flow_code           VARCHAR(32)  NOT NULL DEFAULT 'content_publish'
                      COMMENT '流程模板 content_publish/coupon_push/winback_sms/stock_clear/price_guard',
  action_type         VARCHAR(32)  DEFAULT NULL COMMENT '动作类型 券活动/内容发布/短信触达/库存清理/价格守护',
  biz_goal            VARCHAR(24)  DEFAULT NULL COMMENT '经营目的 拉新/复购/提客单/清库存/唤醒',
  channel_code        VARCHAR(32)  DEFAULT NULL COMMENT '动作投放渠道',

  run_status          TINYINT      NOT NULL DEFAULT 0
                      COMMENT '执行状态 0草稿 1待商家确认 2排队 3执行中 4已结束 5已取消',
  result              TINYINT      NOT NULL DEFAULT 0
                      COMMENT '结果判定 0未判定 1成功 2部分成功 3没成功',
  risk_level          TINYINT      NOT NULL DEFAULT 1 COMMENT '风险 1低 2中 3高（高必须人工确认）',
  need_confirm        TINYINT      NOT NULL DEFAULT 0 COMMENT '1=需要商家点一下才继续',

  budget_cash         DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '计划现金预算',
  budget_coupon_cost  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '计划券成本预算',
  actual_cash         DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实际现金支出',
  actual_coupon_cost  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实际券成本',

  baseline_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '同期基线到手额，算增量用',
  gmv                 DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '带来的成交额（原价）',
  gross_profit        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '带来的毛利',
  uplift_amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '增量 = 实际 - 基线',
  roi_cash            DECIMAL(9,2) NOT NULL DEFAULT 0.00 COMMENT '现金 ROI = 毛利/现金支出（页面 6.3× 用这个）',
  roi_full            DECIMAL(9,2) NOT NULL DEFAULT 0.00 COMMENT '全成本 ROI = 毛利/(现金+券成本)，防止把券成本算成零',
  order_cnt           INT          NOT NULL DEFAULT 0 COMMENT '带来订单数',
  verify_cnt          INT          NOT NULL DEFAULT 0 COMMENT '券核销张数',
  new_member_cnt      INT          NOT NULL DEFAULT 0 COMMENT '带来新会员数',

  plan_start_at       DATETIME     DEFAULT NULL COMMENT '计划开始',
  plan_end_at         DATETIME     DEFAULT NULL COMMENT '计划结束',
  publish_time        DATETIME     DEFAULT NULL COMMENT '实际发布上线时间',
  settle_time         DATETIME     DEFAULT NULL COMMENT '结算复盘时间',

  summary_text        VARCHAR(500) DEFAULT NULL COMMENT '一段能直接转给老板的话',
  fail_reason         VARCHAR(300) DEFAULT NULL COMMENT '没成功的原因（人话，不写技术错误码）',
  owner               VARCHAR(64)  DEFAULT NULL COMMENT '谁在处理',
  eta_date            DATE         DEFAULT NULL COMMENT '预计什么时候能好',
  snapshot_json       JSON         DEFAULT NULL COMMENT '决策依据快照（当时看到的数）',

  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uk_action_no (action_no),
  KEY idx_action_merchant_time (merchant_no, create_time),
  KEY idx_action_result (merchant_no, result, run_status),
  KEY idx_action_channel (merchant_no, channel_code, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 动作主体（执行日志）';


-- -----------------------------------------------------------------------------
-- 8) AI 动作步骤留痕（日志页「它做了这 6 步」+ 回放时间轴）
--    步骤数量不固定：内容发布 6 步，券活动可能 4 步，由 flow_code 决定模板
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_action_step (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  action_id     BIGINT       NOT NULL COMMENT 'ai_action.id',
  step_no       TINYINT      NOT NULL COMMENT '第几步，从 1 开始，按执行顺序',
  step_code     VARCHAR(32)  NOT NULL
                COMMENT 'material/render/submit/review/rewrite/publish/settle/target_pick/copy_write/audit/...',
  step_name     VARCHAR(64)  NOT NULL COMMENT '步骤名（人话），如「提交抖音审核」',
  status        TINYINT      NOT NULL DEFAULT 0
                COMMENT '0待执行 1进行中 2成功 3失败 4被驳回 5跳过',
  title         VARCHAR(128) DEFAULT NULL COMMENT '这一步发生了什么',
  detail        VARCHAR(500) DEFAULT NULL COMMENT '细节，如「勾选 9.9 团购 · 挂门店位置」',
  reject_reason VARCHAR(300) DEFAULT NULL COMMENT '驳回/失败原文，原样保留（老板要看到平台原话）',
  operator      VARCHAR(64)  DEFAULT NULL COMMENT 'AI/系统/商家账号/平台',
  duration_ms   INT          NOT NULL DEFAULT 0 COMMENT '本步耗时',
  occur_time    DATETIME     NOT NULL COMMENT '发生时间，回放按此排序',
  payload_json  JSON         DEFAULT NULL COMMENT '入参出参快照',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uk_step (action_id, step_no),
  KEY idx_step_time (action_id, occur_time),
  KEY idx_step_status (status, occur_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 动作步骤留痕（可回放）';


-- -----------------------------------------------------------------------------
-- 9) 视图：大屏日粒度 / 月粒度（页面直接查视图，别在服务层重算口径）
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_ops_day AS
SELECT
  c.merchant_no,
  c.stat_date,
  SUM(c.impression_cnt)                                                AS impression_cnt,
  SUM(c.visit_cnt)                                                     AS visit_cnt,
  SUM(c.lead_cnt)                                                      AS lead_cnt,
  SUM(c.order_cnt)                                                     AS order_cnt,
  SUM(c.pay_cnt)                                                       AS pay_cnt,
  SUM(c.refund_cnt)                                                    AS refund_cnt,
  SUM(c.gmv)                                                           AS gmv,
  SUM(c.pay_amount)                                                    AS pay_amount,
  SUM(c.refund_amount)                                                 AS refund_amount,
  SUM(c.net_amount)                                                    AS net_amount,
  ROUND(SUM(c.net_amount) / NULLIF(SUM(c.pay_cnt), 0), 2)               AS avg_ticket,
  SUM(c.discount_amount)                                               AS discount_amount,
  SUM(c.coupon_issue_cnt)                                              AS coupon_issue_cnt,
  SUM(c.coupon_verify_cnt)                                             AS coupon_verify_cnt,
  SUM(c.coupon_face_amount)                                            AS coupon_face_amount,
  ROUND(SUM(c.coupon_verify_cnt) / NULLIF(SUM(c.coupon_issue_cnt), 0), 4) AS coupon_verify_rate,
  SUM(c.coupon_cost_amount)                                            AS coupon_cost_amount,
  SUM(c.commission_amount)                                             AS commission_amount,
  SUM(c.cost_amount)                                                   AS cost_amount,
  SUM(c.gross_profit)                                                  AS gross_profit,
  ROUND(SUM(c.gross_profit) / NULLIF(SUM(c.net_amount), 0), 4)          AS gross_margin_rate,
  SUM(CASE WHEN IFNULL(d.channel_type,'offline') <> 'platform' THEN c.net_amount ELSE 0 END) AS own_amount,
  SUM(CASE WHEN IFNULL(d.channel_type,'offline')  = 'platform' THEN c.net_amount ELSE 0 END) AS platform_amount,
  ROUND(SUM(CASE WHEN IFNULL(d.channel_type,'offline') <> 'platform' THEN c.net_amount ELSE 0 END)
        / NULLIF(SUM(c.net_amount), 0), 4)                              AS own_ratio,
  SUM(c.new_member_cnt)                                                AS new_member_cnt,
  SUM(c.member_pay_cnt)                                                AS member_pay_cnt,
  MAX(c.cost_is_estimated)                                             AS cost_is_estimated
FROM channel_daily c
LEFT JOIN ops_channel_dict d ON d.channel_code = c.channel_code
GROUP BY c.merchant_no, c.stat_date;


-- 说明：MySQL 的视图定义不允许 FROM 子句里出现子查询，所以这里直接对
-- channel_daily 分组，不套 v_ops_day。target 列一并进 GROUP BY 以满足
-- ONLY_FULL_GROUP_BY。
CREATE OR REPLACE VIEW v_ops_month AS
SELECT
  c.merchant_no                                                                    AS merchant_no,
  DATE_FORMAT(c.stat_date, '%Y-%m')                                                AS ym,
  MAX(c.stat_date)                                                                 AS last_stat_date,
  COUNT(DISTINCT c.stat_date)                                                      AS days_done,
  SUM(c.net_amount)                                                                AS month_amount,
  SUM(c.pay_cnt)                                                                   AS month_pay_cnt,
  SUM(c.gross_profit)                                                              AS month_gross_profit,
  ROUND(SUM(c.net_amount) / NULLIF(COUNT(DISTINCT c.stat_date), 0), 2)              AS avg_day_amount,
  IFNULL(cfg.month_target_amount, 0)                                               AS month_target_amount,
  ROUND(SUM(c.net_amount) / NULLIF(cfg.month_target_amount, 0), 4)                  AS achieve_rate,
  ROUND(SUM(c.net_amount) / NULLIF(COUNT(DISTINCT c.stat_date), 0)
        * DAY(LAST_DAY(MAX(c.stat_date))), 2)                                      AS forecast_amount,
  ROUND(SUM(c.net_amount) / NULLIF(COUNT(DISTINCT c.stat_date), 0)
        * DAY(LAST_DAY(MAX(c.stat_date)))
        / NULLIF(cfg.month_target_amount, 0), 4)                                   AS forecast_rate,
  ROUND(GREATEST(cfg.month_target_amount - SUM(c.net_amount), 0)
        / NULLIF(DAY(LAST_DAY(MAX(c.stat_date))) - DAY(MAX(c.stat_date)), 0), 2)    AS need_day_amount
FROM channel_daily c
LEFT JOIN merchant_ops_config cfg ON cfg.merchant_no = c.merchant_no
GROUP BY c.merchant_no, DATE_FORMAT(c.stat_date, '%Y-%m'), cfg.month_target_amount;
