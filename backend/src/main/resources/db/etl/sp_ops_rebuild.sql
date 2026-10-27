-- =============================================================================
-- 运营数据中台 ETL —— 渠道日汇总 / 漏斗日汇总
--
-- 放在 db/etl/ 而不是 db/migration/ 的原因：
--   Flyway 一旦执行失败会阻塞应用启动。ETL 依赖「商品成本、券成本归属、渠道
--   归因打点」三件业务口径，首次上线必须在测试库 dry-run 对账。因此独立成脚
--   本，由 DBA 或发布单手工执行。
--
-- 执行方式：
--   mysql -u root -p ibigou_blindbox < db/etl/sp_ops_rebuild.sql
--   CALL sp_ops_rebuild('M0001', '2026-10-01', '2026-10-25');
--
-- 幂等性：
--   只删除并重建 src='etl' 的行，src='manual'（运营手工修正）原样保留。
--   同一天同一区间重跑 N 次结果一致。
--
-- 本脚本当前能自动算出来的：
--   ✔ 支付单数 / 成交额 / 实收 / 退款 / 净实收      ← offline_order
--   ✔ 发券数 / 核销数 / 券面额 / 让利               ← user_coupon
--   ✔ 券成本 / 平台佣金 / 商品成本（估算）/ 毛利    ← 配置反算
--   ✔ 漏斗 进店 / 领券 / 核销 三步                   ← 上面两个表汇总
--   ✔ 让利含满减（原价−应付−余额抵扣，与券面额取大） ← offline_order 6b
--   ✔ 新客数（首单口径，非注册口径）                 ← offline_order 6c
--   ✔ 二次到店 + 30 日复购率                         ← offline_order 7a/7b
--   ✔ 叫号组数 / 平均等待秒                          ← merchant_queue_ticket 7b
--   ✘ 曝光数               —— 需要前端埋点，暂无来源，留 0 待补
--   ✘ 注册会员数           —— member_profile 没有注册时间列，改用首单口径顶上，
--                              语义是「今天第一次来买的客人」，别当注册数讲
--   ✘ 商品成本真值         —— 需要商品进货价表，当前按 goods_margin_rate 反算
--                              （写入行 cost_is_estimated=1，页面必须标注「估算」）
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_ops_rebuild;

DELIMITER $$

-- ⚠ 坑（生产踩过，别删）：p_merchant 必须显式 COLLATE utf8mb4_general_ci
--   生产库默认 collation 是 utf8mb4_unicode_ci，而业务表（offline_order / user_coupon /
--   channel_daily …）的 merchant_no 列是 utf8mb4_general_ci。存储过程参数若不显式声明，
--   会继承库的 unicode_ci，调用时 `merchant_no = p_merchant` 直接报
--   ERROR 1267 Illegal mix of collations，ETL 一行都跑不出来。
CREATE PROCEDURE sp_ops_rebuild(
  IN p_merchant VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci,
  IN p_from     DATE,
  IN p_to       DATE
)
BEGIN
  DECLARE v_goods_margin DECIMAL(5,4) DEFAULT 0.4000;  -- 商品毛利率（不含券成本与佣金）
  DECLARE v_coupon_ratio DECIMAL(5,4) DEFAULT 0.4200;  -- 券成本 / 券面额
  DECLARE v_comm_rate    DECIMAL(5,4) DEFAULT 0.0250;  -- 平台默认佣金率

  IF p_from IS NULL OR p_to IS NULL OR p_from > p_to THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'SP_OPS_REBUILD: 日期区间非法';
  END IF;

  SELECT IFNULL(goods_margin_rate, 0.4000),
         IFNULL(coupon_cost_ratio, 0.4200),
         IFNULL(commission_rate_default, 0.0250)
    INTO v_goods_margin, v_coupon_ratio, v_comm_rate
    FROM merchant_ops_config
   WHERE merchant_no = p_merchant
   LIMIT 1;

  -- ---------------------------------------------------------------------------
  -- 1) 清空区间内的自动汇总行（手工修正行不动）
  -- ---------------------------------------------------------------------------
  DELETE FROM channel_daily
   WHERE merchant_no = p_merchant
     AND stat_date BETWEEN p_from AND p_to
     AND src = 'etl';

  -- ---------------------------------------------------------------------------
  -- 2) 骨架行：把区间内出现过的 (日期, 渠道) 全部铺出来，后面才能 UPDATE 落数
  -- ---------------------------------------------------------------------------
  INSERT IGNORE INTO channel_daily (merchant_no, stat_date, channel_code, src)
  SELECT merchant_no, stat_date, channel_code, 'etl'
    FROM (
      -- 支付
      SELECT o.merchant_no,
             DATE(o.pay_time)                                AS stat_date,
             IFNULL(NULLIF(o.channel_code, ''), 'walkin')     AS channel_code
        FROM offline_order o
       WHERE o.merchant_no = p_merchant
         AND o.pay_time >= p_from
         AND o.pay_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
      UNION
      -- 退款
      SELECT o.merchant_no,
             DATE(o.refund_time),
             IFNULL(NULLIF(o.channel_code, ''), 'walkin')
        FROM offline_order o
       WHERE o.merchant_no = p_merchant
         AND o.refund_amount > 0
         AND o.refund_time >= p_from
         AND o.refund_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
      UNION
      -- 发券（发券渠道可能当天没有成交，但核销率分母要算它）
      SELECT c.source_merchant_no,
             DATE(c.create_time),
             IFNULL(NULLIF(c.acquire_channel_code, ''), 'other')
        FROM user_coupon c
       WHERE c.source_merchant_no = p_merchant
         AND c.create_time >= p_from
         AND c.create_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
    ) t;

  -- ---------------------------------------------------------------------------
  -- 3) 支付：单量 / 成交额 / 实收 / 净实收
  --    退款单（refund_status=1）不计入支付，避免与第 4 步重复扣减
  -- ---------------------------------------------------------------------------
  UPDATE channel_daily cd
    JOIN (
      SELECT o.merchant_no,
             DATE(o.pay_time)                              AS d,
             IFNULL(NULLIF(o.channel_code, ''), 'walkin')   AS ch,
             COUNT(*)                                       AS cnt,
             IFNULL(SUM(o.order_amount), 0)                 AS gmv_amt,
             IFNULL(SUM(o.pay_amount), 0)                   AS pay_amt
        FROM offline_order o
       WHERE o.merchant_no = p_merchant
         AND o.pay_time >= p_from
         AND o.pay_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
         AND o.refund_status <> 1
       GROUP BY o.merchant_no,
                DATE(o.pay_time),
                IFNULL(NULLIF(o.channel_code, ''), 'walkin')
    ) o
      ON o.merchant_no = cd.merchant_no
     AND o.d           = cd.stat_date
     AND o.ch          = cd.channel_code
     SET cd.order_cnt    = o.cnt,
         cd.pay_cnt      = o.cnt,
         cd.gmv          = o.gmv_amt,
         cd.pay_amount   = o.pay_amt,
         cd.net_amount   = o.pay_amt - cd.refund_amount
   WHERE cd.merchant_no = p_merchant
     AND cd.stat_date BETWEEN p_from AND p_to
     AND cd.src = 'etl';

  -- ---------------------------------------------------------------------------
  -- 4) 退款：按退款发生日归集（不在原支付日冲减，便于看当日真实现金流）
  -- ---------------------------------------------------------------------------
  UPDATE channel_daily cd
    JOIN (
      SELECT o.merchant_no,
             DATE(o.refund_time)                            AS d,
             IFNULL(NULLIF(o.channel_code, ''), 'walkin')    AS ch,
             COUNT(*)                                        AS cnt,
             IFNULL(SUM(o.refund_amount), 0)                 AS amt
        FROM offline_order o
       WHERE o.merchant_no = p_merchant
         AND o.refund_amount > 0
         AND o.refund_time >= p_from
         AND o.refund_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
       GROUP BY o.merchant_no,
                DATE(o.refund_time),
                IFNULL(NULLIF(o.channel_code, ''), 'walkin')
    ) r
      ON r.merchant_no = cd.merchant_no
     AND r.d           = cd.stat_date
     AND r.ch          = cd.channel_code
     SET cd.refund_cnt    = r.cnt,
         cd.refund_amount = r.amt,
         cd.net_amount    = cd.pay_amount - r.amt
   WHERE cd.merchant_no = p_merchant
     AND cd.stat_date BETWEEN p_from AND p_to
     AND cd.src = 'etl';

  -- ---------------------------------------------------------------------------
  -- 5a) 发券数：按领取日 + 领券渠道
  -- ---------------------------------------------------------------------------
  UPDATE channel_daily cd
    JOIN (
      SELECT c.source_merchant_no                          AS merchant_no,
             DATE(c.create_time)                           AS d,
             IFNULL(NULLIF(c.acquire_channel_code, ''), 'other') AS ch,
             COUNT(*)                                      AS cnt
        FROM user_coupon c
       WHERE c.source_merchant_no = p_merchant
         AND c.create_time >= p_from
         AND c.create_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
       GROUP BY c.source_merchant_no,
                DATE(c.create_time),
                IFNULL(NULLIF(c.acquire_channel_code, ''), 'other')
    ) ci
      ON ci.merchant_no = cd.merchant_no
     AND ci.d           = cd.stat_date
     AND ci.ch          = cd.channel_code
     SET cd.coupon_issue_cnt = ci.cnt
   WHERE cd.merchant_no = p_merchant
     AND cd.stat_date BETWEEN p_from AND p_to
     AND cd.src = 'etl';

  -- ---------------------------------------------------------------------------
  -- 5b) 核销：按核销日 + 核销渠道
  --     核销日优先取关联订单支付日，缺关联（手工核销）则退回 update_time
  --     渠道优先取订单归因渠道，其次券的领取渠道 —— 因为「抖音领券、到店核销」
  --     应该算到店转化，而不是抖音 GMV
  -- ---------------------------------------------------------------------------
  UPDATE channel_daily cd
    JOIN (
      SELECT c.source_merchant_no                                   AS merchant_no,
             DATE(IFNULL(o.pay_time, c.update_time))                 AS d,
             IFNULL(NULLIF(IFNULL(o.channel_code, c.acquire_channel_code), ''), 'walkin') AS ch,
             COUNT(*)                                               AS vcnt,
             IFNULL(SUM(IFNULL(c.face_amount, c.prize_value)), 0)    AS face_amt
        FROM user_coupon c
        LEFT JOIN offline_order o ON o.offline_order_no = c.biz_no
       WHERE c.source_merchant_no = p_merchant
         AND c.status = 1
         AND DATE(IFNULL(o.pay_time, c.update_time)) BETWEEN p_from AND p_to
       GROUP BY c.source_merchant_no,
                DATE(IFNULL(o.pay_time, c.update_time)),
                IFNULL(NULLIF(IFNULL(o.channel_code, c.acquire_channel_code), ''), 'walkin')
    ) cv
      ON cv.merchant_no = cd.merchant_no
     AND cv.d           = cd.stat_date
     AND cv.ch          = cd.channel_code
     SET cd.coupon_verify_cnt  = cv.vcnt,
         cd.coupon_face_amount = cv.face_amt,
         cd.discount_amount    = cv.face_amt  -- 满减/折价暂无来源，先等于券面额
   WHERE cd.merchant_no = p_merchant
     AND cd.stat_date BETWEEN p_from AND p_to
     AND cd.src = 'etl';

  -- ---------------------------------------------------------------------------
  -- 6) 成本 / 佣金 / 毛利（全部用配置反算，行上打 cost_is_estimated=1）
  --    毛利 = 净实收 - 商品成本 - 券成本 - 佣金
  --    大屏「毛利率」= 毛利 / 净实收，与 gross_margin_target 对比
  -- ---------------------------------------------------------------------------
  UPDATE channel_daily cd
    LEFT JOIN ops_channel_dict d ON d.channel_code = cd.channel_code
     SET cd.coupon_cost_amount = ROUND(cd.coupon_face_amount * v_coupon_ratio, 2),
         cd.commission_amount  = ROUND(cd.net_amount * IFNULL(d.commission_rate, v_comm_rate), 2),
         cd.cost_amount        = ROUND(cd.net_amount * (1 - v_goods_margin), 2),
         cd.cost_is_estimated  = 1,
         cd.gross_profit       = ROUND(
                                   cd.net_amount
                                   - cd.net_amount * (1 - v_goods_margin)
                                   - cd.coupon_face_amount * v_coupon_ratio
                                   - cd.net_amount * IFNULL(d.commission_rate, v_comm_rate), 2)
   WHERE cd.merchant_no = p_merchant
     AND cd.stat_date BETWEEN p_from AND p_to
     AND cd.src = 'etl';

  -- ---------------------------------------------------------------------------
  -- 6b) 让利补满减：券面额只覆盖「券」，满减/折价来自订单原价与应付的差额
  --     discount_amount = GREATEST(券面额, 原价−应付)，缺券面额时也不会算成 0
  --     注意：deduct_balance 是顾客余额抵扣，不是商家让利，必须排掉
  -- ---------------------------------------------------------------------------
  UPDATE channel_daily cd
    JOIN (
      SELECT o.merchant_no                                                  AS merchant_no,
             DATE(o.pay_time)                                                AS d,
             IFNULL(NULLIF(o.channel_code, ''), 'walkin')                    AS ch,
             IFNULL(SUM(GREATEST(IFNULL(o.order_amount, 0) - IFNULL(o.pay_amount, 0)
                                 - IFNULL(o.deduct_balance, 0), 0)), 0)      AS disc
        FROM offline_order o
       WHERE o.merchant_no = p_merchant
         AND o.order_status IN (1, 3)
         AND o.pay_time >= p_from
         AND o.pay_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
       GROUP BY o.merchant_no, DATE(o.pay_time), IFNULL(NULLIF(o.channel_code, ''), 'walkin')
    ) dz
      ON dz.merchant_no = cd.merchant_no
     AND dz.d           = cd.stat_date
     AND dz.ch          = cd.channel_code
     SET cd.discount_amount = GREATEST(cd.discount_amount, dz.disc)
   WHERE cd.merchant_no = p_merchant
     AND cd.stat_date BETWEEN p_from AND p_to
     AND cd.src = 'etl';

  -- ---------------------------------------------------------------------------
  -- 6c) 新客数：当日下单且「该手机号在本商家首次下单就是今天」的人数
  --     member_profile 没有注册时间列，注册口径算不出来，所以这里用首单口径，
  --     语义是「今天第一次来买的客人」，不是「今天注册的会员」——别混着讲
  -- ---------------------------------------------------------------------------
  UPDATE channel_daily cd
    JOIN (
      SELECT o.merchant_no                                AS merchant_no,
             DATE(o.pay_time)                              AS d,
             IFNULL(NULLIF(o.channel_code, ''), 'walkin')  AS ch,
             COUNT(*)                                      AS cnt
        FROM offline_order o
        JOIN (
          SELECT merchant_no, user_phone, MIN(pay_time) AS first_pay
            FROM offline_order
           WHERE merchant_no = p_merchant AND order_status IN (1, 3) AND pay_time IS NOT NULL
           GROUP BY merchant_no, user_phone
        ) f
          ON f.merchant_no = o.merchant_no
         AND f.user_phone  = o.user_phone
         AND f.first_pay   = o.pay_time
       WHERE o.merchant_no = p_merchant
         AND o.order_status IN (1, 3)
         AND o.pay_time >= p_from
         AND o.pay_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
       GROUP BY o.merchant_no, DATE(o.pay_time), IFNULL(NULLIF(o.channel_code, ''), 'walkin')
    ) nm
      ON nm.merchant_no = cd.merchant_no
     AND nm.d           = cd.stat_date
     AND nm.ch          = cd.channel_code
     SET cd.new_member_cnt = nm.cnt
   WHERE cd.merchant_no = p_merchant
     AND cd.stat_date BETWEEN p_from AND p_to
     AND cd.src = 'etl';

  -- ---------------------------------------------------------------------------
  -- 7) 漏斗：先清后建，五步固定顺序
  --    exposure 需要埋点（无来源，留 0，页面「待接入」）
  --    repurchase 用「近 30 天内第 2 次及以上到店」的人数，见 7a
  -- ---------------------------------------------------------------------------
  DELETE FROM funnel_daily
   WHERE merchant_no = p_merchant
     AND stat_date BETWEEN p_from AND p_to
     AND channel_code = 'all'
     AND src = 'etl';

  INSERT INTO funnel_daily
    (merchant_no, stat_date, channel_code, step_code, step_no, step_name, unit, user_cnt, src)
  SELECT p_merchant, s.stat_date, 'all', 'exposure', 1, '内容曝光', '次',
         IFNULL(SUM(s.impression_cnt), 0), 'etl'
    FROM channel_daily s
   WHERE s.merchant_no = p_merchant AND s.stat_date BETWEEN p_from AND p_to
   GROUP BY s.stat_date;

  INSERT INTO funnel_daily
    (merchant_no, stat_date, channel_code, step_code, step_no, step_name, unit, user_cnt, src)
  SELECT p_merchant, s.stat_date, 'all', 'visit', 2, '进店 / 点开', '人',
         IFNULL(SUM(s.visit_cnt), 0), 'etl'
    FROM channel_daily s
   WHERE s.merchant_no = p_merchant AND s.stat_date BETWEEN p_from AND p_to
   GROUP BY s.stat_date;

  INSERT INTO funnel_daily
    (merchant_no, stat_date, channel_code, step_code, step_no, step_name, unit, user_cnt, src)
  SELECT p_merchant, s.stat_date, 'all', 'lead', 3, '领券 / 加购', '张',
         IFNULL(SUM(s.coupon_issue_cnt), 0), 'etl'
    FROM channel_daily s
   WHERE s.merchant_no = p_merchant AND s.stat_date BETWEEN p_from AND p_to
   GROUP BY s.stat_date;

  INSERT INTO funnel_daily
    (merchant_no, stat_date, channel_code, step_code, step_no, step_name, unit, user_cnt, src)
  SELECT p_merchant, s.stat_date, 'all', 'verify', 4, '到店核销', '张',
         IFNULL(SUM(s.coupon_verify_cnt), 0), 'etl'
    FROM channel_daily s
   WHERE s.merchant_no = p_merchant AND s.stat_date BETWEEN p_from AND p_to
   GROUP BY s.stat_date;

  INSERT INTO funnel_daily
    (merchant_no, stat_date, channel_code, step_code, step_no, step_name, unit, user_cnt, src)
  SELECT p_merchant, s.stat_date, 'all', 'repurchase', 5, '二次到店', '人', 0, 'etl'
    FROM channel_daily s
   WHERE s.merchant_no = p_merchant AND s.stat_date BETWEEN p_from AND p_to
   GROUP BY s.stat_date;

  -- 转化率：自连接取上一步，避免窗口函数在 5.7 上不可用
  UPDATE funnel_daily f
    JOIN funnel_daily p
      ON p.merchant_no  = f.merchant_no
     AND p.stat_date    = f.stat_date
     AND p.channel_code = f.channel_code
     AND p.step_no      = f.step_no - 1
     SET f.prev_cnt  = p.user_cnt,
         f.conv_rate = ROUND(f.user_cnt / NULLIF(p.user_cnt, 0), 4)
   WHERE f.merchant_no = p_merchant
     AND f.stat_date BETWEEN p_from AND p_to
     AND f.channel_code = 'all';

  -- 7a) 二次到店：当日成交客户里「此前 30 天也买过」的回头客人数
  --     口径必须与 OpsService 实时聚合一致。修正前这里统计的是「30 天内下单≥2次的人」
  --     （累计口径），分母却是当日核销张数，两个窗口不相除，算出过 200% 的转化率。
  UPDATE funnel_daily f
    JOIN (
      SELECT o.merchant_no    AS merchant_no,
             DATE(o.pay_time) AS d,
             COUNT(DISTINCT o.user_phone) AS cnt
        FROM offline_order o
       WHERE o.merchant_no = p_merchant
         AND o.order_status = 1
         AND o.pay_time >= p_from
         AND o.pay_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
         AND EXISTS (SELECT 1 FROM offline_order p
                      WHERE p.merchant_no  = o.merchant_no
                        AND p.user_phone   = o.user_phone
                        AND p.order_status = 1
                        AND p.pay_time >= DATE_SUB(DATE(o.pay_time), INTERVAL 30 DAY)
                        AND p.pay_time <  DATE(o.pay_time))
       GROUP BY o.merchant_no, DATE(o.pay_time)
    ) rp
      ON rp.merchant_no = f.merchant_no
     AND rp.d           = f.stat_date
     SET f.user_cnt = rp.cnt
   WHERE f.merchant_no = p_merchant
     AND f.stat_date BETWEEN p_from AND p_to
     AND f.channel_code = 'all'
     AND f.step_code = 'repurchase'
     AND f.src = 'etl';

  -- 7a-2) 二次到店的转化率分母必须是「当日成交客户数」，不能拿上一步的核销张数
  UPDATE funnel_daily f
    JOIN (
      SELECT merchant_no, DATE(pay_time) AS d, COUNT(DISTINCT user_phone) AS payers
        FROM offline_order
       WHERE merchant_no = p_merchant
         AND order_status = 1
         AND pay_time >= p_from
         AND pay_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
       GROUP BY merchant_no, DATE(pay_time)
    ) py
      ON py.merchant_no = f.merchant_no
     AND py.d           = f.stat_date
     SET f.prev_cnt  = py.payers,
         f.conv_rate = ROUND(f.user_cnt / NULLIF(py.payers, 0), 4)
   WHERE f.merchant_no = p_merchant
     AND f.stat_date BETWEEN p_from AND p_to
     AND f.channel_code = 'all'
     AND f.step_code = 'repurchase'
     AND f.src = 'etl';

  -- 7b) 门店级指标：复购率 / 叫号组数 / 平均等待秒
  --     叫号数据之前一直缺来源，merchant_queue_ticket 本来就有，直接汇进来
  DELETE FROM ops_metric_daily
   WHERE merchant_no = p_merchant
     AND stat_date BETWEEN p_from AND p_to
     AND src = 'etl';

  INSERT INTO ops_metric_daily
    (merchant_no, stat_date, metric_code, metric_name, metric_value, unit, src)
  SELECT p_merchant, d.stat_date, 'repurchase_rate_30d', '30日复购率',
         ROUND(IFNULL(r.cnt, 0) / NULLIF(d.payers, 0), 4), '%', 'etl'
    FROM (
      SELECT DATE(o.pay_time) AS stat_date, COUNT(DISTINCT o.user_phone) AS payers
        FROM offline_order o
       WHERE o.merchant_no = p_merchant AND o.order_status = 1
         AND o.pay_time >= p_from AND o.pay_time < DATE_ADD(p_to, INTERVAL 1 DAY)
       GROUP BY DATE(o.pay_time)
    ) d
    LEFT JOIN (
      SELECT DATE(o.pay_time) AS stat_date, COUNT(DISTINCT o.user_phone) AS cnt
        FROM offline_order o
        JOIN (
          SELECT merchant_no, user_phone
            FROM offline_order
           WHERE merchant_no = p_merchant AND order_status = 1
             AND pay_time >= DATE_SUB(p_from, INTERVAL 30 DAY)
             AND pay_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
           GROUP BY merchant_no, user_phone
          HAVING COUNT(*) >= 2
        ) r2 ON r2.merchant_no = o.merchant_no AND r2.user_phone = o.user_phone
       WHERE o.merchant_no = p_merchant AND o.order_status = 1
         AND o.pay_time >= p_from AND o.pay_time < DATE_ADD(p_to, INTERVAL 1 DAY)
       GROUP BY DATE(o.pay_time)
    ) r ON r.stat_date = d.stat_date;

  INSERT INTO ops_metric_daily
    (merchant_no, stat_date, metric_code, metric_name, metric_value, unit, src)
  SELECT p_merchant, DATE(t.create_time), 'queue_total', '叫号组数', COUNT(*), '组', 'etl'
    FROM merchant_queue_ticket t
   WHERE t.merchant_no = p_merchant
     AND t.create_time >= p_from
     AND t.create_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
   GROUP BY DATE(t.create_time);

  INSERT INTO ops_metric_daily
    (merchant_no, stat_date, metric_code, metric_name, metric_value, unit, src)
  SELECT p_merchant, DATE(t.create_time), 'queue_wait_avg_sec', '平均等待',
         ROUND(AVG(TIMESTAMPDIFF(SECOND, t.create_time, t.call_time)), 0), '秒', 'etl'
    FROM merchant_queue_ticket t
   WHERE t.merchant_no = p_merchant
     AND t.call_time IS NOT NULL
     AND t.create_time >= p_from
     AND t.create_time <  DATE_ADD(p_to, INTERVAL 1 DAY)
   GROUP BY DATE(t.create_time);

  -- ---------------------------------------------------------------------------
  -- 8) 回执：跑完把区间汇总打出来，方便对账
  -- ---------------------------------------------------------------------------
  SELECT p_merchant AS merchant_no,
         p_from     AS stat_from,
         p_to       AS stat_to,
         IFNULL(SUM(net_amount), 0)         AS net_amount,
         IFNULL(SUM(pay_cnt), 0)            AS pay_cnt,
         IFNULL(SUM(gross_profit), 0)       AS gross_profit,
         ROUND(IFNULL(SUM(gross_profit), 0) / NULLIF(SUM(net_amount), 0), 4) AS gross_margin_rate,
         IFNULL(SUM(coupon_issue_cnt), 0)   AS coupon_issue_cnt,
         IFNULL(SUM(coupon_verify_cnt), 0)  AS coupon_verify_cnt,
         COUNT(*)                           AS channel_rows
    FROM channel_daily
   WHERE merchant_no = p_merchant
     AND stat_date BETWEEN p_from AND p_to;
END$$

DELIMITER ;
