-- ============================================================================
-- V25 物业 AI · 操作级三屏的数据底座
--   1) property_action_log  动作留痕（三级授权 L1/L2/L3 + 5 分钟撤回 + 谁批的）
--   2) property_bill_relief 单户账单减免申请（按角色额度分级，超限只出证据包不改金额）
-- 说明：prod 关闭 flyway，本文件需在宿主机 mysql 手动执行；全部语句幂等，可重复跑。
-- ============================================================================

-- 1. 动作留痕 -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS property_action_log (
  log_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  company_id    BIGINT       NOT NULL DEFAULT 0    COMMENT '公司ID',
  admin_id      BIGINT       NULL                  COMMENT '发起人ID',
  admin_name    VARCHAR(64)  NULL                  COMMENT '发起人姓名',
  action        VARCHAR(64)  NOT NULL              COMMENT '动作键 dispatch/relief/undo/...',
  action_label  VARCHAR(128) NULL                  COMMENT '动作中文名',
  target_type   VARCHAR(32)  NULL                  COMMENT '对象类型 workorder/bill/violation/...',
  target_id     BIGINT       NULL                  COMMENT '对象ID',
  target_label  VARCHAR(160) NULL                  COMMENT '对象可读标识（单号/房号）',
  scope_desc    VARCHAR(255) NULL                  COMMENT '影响面',
  auth_level    VARCHAR(8)   NOT NULL DEFAULT 'L2' COMMENT '授权档 L1只读直答/L2出草稿/L3只能你做',
  status        TINYINT      NOT NULL DEFAULT 1    COMMENT '0待执行 1已执行 2已撤回 3已驳回',
  before_json   TEXT         NULL                  COMMENT '执行前快照',
  after_json    TEXT         NULL                  COMMENT '执行后快照/AI依据',
  note          VARCHAR(500) NULL                  COMMENT '说明/驳回原因',
  undo_deadline DATETIME     NULL                  COMMENT '可撤回截止时间（执行后 5 分钟）',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id),
  KEY idx_al_company_time (company_id, create_time),
  KEY idx_al_target (target_type, target_id),
  KEY idx_al_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业动作留痕（三级授权+撤回）';

-- 2. 单户账单减免申请 ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS property_bill_relief (
  relief_id     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  company_id    BIGINT        NOT NULL DEFAULT 0     COMMENT '公司ID',
  bill_id       BIGINT        NOT NULL               COMMENT '账单ID',
  owner_id      BIGINT        NOT NULL               COMMENT '业主ID',
  room_id       BIGINT        NULL                   COMMENT '房间ID',
  relief_type   TINYINT       NOT NULL DEFAULT 1     COMMENT '1滞纳金 2本金 3整单',
  apply_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '申请减免金额',
  quota_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '该角色单笔额度',
  auth_level    VARCHAR(8)    NOT NULL DEFAULT 'L3'  COMMENT '授权档（减免恒为 L3）',
  status        TINYINT       NOT NULL DEFAULT 0     COMMENT '0待批 1已批 2已驳 3超限只出证据包',
  applicant_id  BIGINT        NULL                   COMMENT '申请人ID',
  applicant_name VARCHAR(64)  NULL                   COMMENT '申请人姓名',
  approver_id   BIGINT        NULL                   COMMENT '审批人ID（不得为申请人）',
  approver_name VARCHAR(64)   NULL                   COMMENT '审批人姓名',
  reason        VARCHAR(500)  NULL                   COMMENT '减免理由',
  evidence_json TEXT          NULL                   COMMENT '证据包（账单+抵扣流水+历史+同户型对比）',
  approve_note  VARCHAR(500)  NULL                   COMMENT '审批意见',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (relief_id),
  KEY idx_rel_company_status (company_id, status),
  KEY idx_rel_bill (bill_id),
  KEY idx_rel_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单户账单减免申请（分级额度）';

-- 3. 补字段（幂等）：动作日志需要记录同一批号，便于「一次确认 N 件」在留痕里成组
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'property_action_log' AND column_name = 'batch_no');
SET @s := IF(@c = 0,
  'ALTER TABLE property_action_log ADD COLUMN batch_no VARCHAR(48) NULL COMMENT ''批次号（一次确认成组）''',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 4. property_owner 加微信 openid（业主端「微信一键登录」；唯一但允许 NULL）
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'property_owner' AND column_name = 'wx_openid');
SET @s := IF(@c = 0,
  'ALTER TABLE property_owner ADD COLUMN wx_openid VARCHAR(64) NULL COMMENT ''公众号 openid''',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'property_owner' AND index_name = 'uk_owner_wx_openid');
SET @s := IF(@c = 0,
  'ALTER TABLE property_owner ADD UNIQUE KEY uk_owner_wx_openid (wx_openid)',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
