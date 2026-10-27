-- =============================================================================
-- V21 —— 中奖播报结构化字段
--
-- 背景（大屏中奖弹屏"场景要按中奖内容设计"的前置改造）：
--   announce_log.content 是自由文本，中奖播报有 3 套并存的话术模板：
--     A 后端开奖段：恭喜您的盲盒开中了立减5.00元券。
--     B 前端结算段：宜必购便民生活圈…恭喜您开出¥5 立减券奖品…
--     C 老模板：您好，这是本店第28位参与盲盒活动的用户，恭喜您中奖，盲盒优惠8.00折…
--   电视大屏过去只能拿 content 去正则猜奖品类型（/免/、/余额/、/折/、/券/），
--   猜错就选错场景；而且同一次抽奖会落 2~3 条 draw 记录（后端开奖段 + 前端结算段
--   触发两次 announce-prize），大屏"最近中奖"列表出现重复条目。
--
-- 本迁移只做「加可空列 + 补索引」，不改任何现有列语义，老代码零影响：
--   prize_type    奖品类型，与 PrizeType 枚举一致（1折扣券 2立减券 3余额 4团购免单 5谢谢参与）
--   prize_value   奖品数值（折扣券=折数，立减/余额=金额）
--   customer_seq  本店第几位顾客（= box_prize_limit_stat 该商家累计人次）
--   is_primary    是否为权威开奖记录（1=是）。同一次抽奖只有一条 is_primary=1，
--                 大屏只认它，避免重复弹窗；其余重复记录保留作语音播报。
--
-- 铁律：
--   - 只加可空列，老数据 NULL 不影响任何查询（TV 侧对 NULL 有兜底解析）
--   - 幂等：重复执行不报错
--   - 不动 announce_log 的 content/event_type 语义
-- =============================================================================

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_v21_announce_prize_fields$$
CREATE PROCEDURE sp_v21_announce_prize_fields()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'announce_log'
                   AND COLUMN_NAME = 'prize_type') THEN
    ALTER TABLE announce_log
      ADD COLUMN prize_type   TINYINT(3)      DEFAULT NULL
        COMMENT '奖品类型 1折扣券 2立减券 3余额 4团购免单 5谢谢参与（NULL=非中奖或历史数据）'
        AFTER event_type,
      ADD COLUMN prize_value  DECIMAL(10,2)   DEFAULT NULL
        COMMENT '奖品数值：折扣券=折数，立减券/余额=金额' AFTER prize_type,
      ADD COLUMN customer_seq INT(11)         DEFAULT NULL
        COMMENT '本店第几位顾客（= box_prize_limit_stat 该商家累计人次）' AFTER prize_value,
      ADD COLUMN is_primary   TINYINT(1)      DEFAULT 0
        COMMENT '是否权威开奖记录 1=是（同一次抽奖仅一条，大屏只认它）' AFTER customer_seq;
  END IF;

  -- 大屏按「权威开奖记录 + 时间倒序」取最近中奖，加联合索引
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'announce_log'
                   AND INDEX_NAME = 'idx_announce_primary') THEN
    ALTER TABLE announce_log
      ADD KEY idx_announce_primary (merchant_no, event_type, is_primary, announce_id);
  END IF;
END$$
CALL sp_v21_announce_prize_fields()$$
DROP PROCEDURE sp_v21_announce_prize_fields$$
DELIMITER ;

-- -----------------------------------------------------------------------------
-- 历史数据回填（尽力而为，解析不出就留 NULL，TV 侧有正则兜底）
--   只处理「后端开奖段」模板（内容最规整）：
--     恭喜您的盲盒开中了立减5.00元券。   → prize_type=2, prize_value=5.00
--     恭喜您的盲盒开中了余额10.00元。     → prize_type=3, prize_value=10.00
--     恭喜您的盲盒开中了9.00折优惠券。   → prize_type=1, prize_value=9.00
--   MySQL 5.7 无 REGEXP_SUBSTR，用 SUBSTRING_INDEX + LOCATE 组合切数字
-- -----------------------------------------------------------------------------
UPDATE announce_log
SET prize_type = 2,
    prize_value = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(content, '立减', -1), '元券', 1)) AS DECIMAL(10,2)),
    is_primary = 1
WHERE event_type = 'draw'
  AND content LIKE '恭喜您的盲盒开中了立减%'
  AND prize_type IS NULL;

UPDATE announce_log
SET prize_type = 3,
    prize_value = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(content, '余额', -1), '元', 1)) AS DECIMAL(10,2)),
    is_primary = 1
WHERE event_type = 'draw'
  AND content LIKE '恭喜您的盲盒开中了余额%'
  AND prize_type IS NULL;

UPDATE announce_log
SET prize_type = 1,
    prize_value = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(content, '开中了', -1), '折', 1)) AS DECIMAL(10,2)),
    is_primary = 1
WHERE event_type = 'draw'
  AND content LIKE '恭喜您的盲盒开中了%折优惠券%'
  AND prize_type IS NULL;

-- 「谢谢参与」不该被播报成「恭喜开中了余额0.00元」——这类脏记录清掉结构化标记，
-- 让 TV 侧识别为「未中奖」而非弹中奖屏。
UPDATE announce_log
SET prize_type = 5
WHERE event_type = 'draw'
  AND content LIKE '%0.00元%'
  AND prize_type IS NULL;

-- -----------------------------------------------------------------------------
-- 历史回填（第二批）：老模板「盲盒优惠8.00折 / 立减5.00元 / 余额10.00元」
--   这是 2026-09 上线前的播报模板，同样是一条开奖记录，只是不是 A 模板。
--   解析出奖品类型与数值，并标记为权威记录，让 TV 历史数据也能正确分场景。
-- -----------------------------------------------------------------------------
UPDATE announce_log
SET prize_type = 1,
    prize_value = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(content, '盲盒优惠', -1), '折', 1)) AS DECIMAL(10,2)),
    is_primary = 1
WHERE event_type = 'draw'
  AND content LIKE '%盲盒优惠%折%'
  AND content NOT LIKE '%盲盒优惠立减%'
  AND content NOT LIKE '%盲盒优惠余额%'
  AND prize_type IS NULL;

UPDATE announce_log
SET prize_type = 2,
    prize_value = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(content, '盲盒优惠立减', -1), '元', 1)) AS DECIMAL(10,2)),
    is_primary = 1
WHERE event_type = 'draw'
  AND content LIKE '%盲盒优惠立减%'
  AND prize_type IS NULL;

UPDATE announce_log
SET prize_type = 3,
    prize_value = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(content, '盲盒优惠余额', -1), '元', 1)) AS DECIMAL(10,2)),
    is_primary = 1
WHERE event_type = 'draw'
  AND content LIKE '%盲盒优惠余额%'
  AND content NOT LIKE '%余额0.00元%'
  AND prize_type IS NULL;

-- -----------------------------------------------------------------------------
-- 历史回填（第三批）：customer_seq「本店第X位顾客」
--   两套话术各有一种写法：
--     B结算段：您是本店第47位顾客        → SUBSTRING_INDEX(content,'本店第',-1) 再切 '位'
--     C老模板：这是本店第28位参与盲盒... → 同上
-- -----------------------------------------------------------------------------
UPDATE announce_log
SET customer_seq = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(content, '本店第', -1), '位', 1)) AS UNSIGNED)
WHERE event_type = 'draw'
  AND content LIKE '%本店第%位顾客%'
  AND customer_seq IS NULL;

UPDATE announce_log
SET customer_seq = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(content, '本店第', -1), '位', 1)) AS UNSIGNED)
WHERE event_type = 'draw'
  AND content LIKE '%本店第%位参与盲盒%'
  AND customer_seq IS NULL;
