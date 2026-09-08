-- ============================================================================
-- 宜必购盲盒优惠券 & 余额系统  数据库建表脚本
-- 依据：需求规格说明书 V1.4 第 6 章《数据表详细设计》
-- 引擎：InnoDB / 字符集：utf8mb4 / 数据库：ibigou_blindbox
-- 约定：
--   * 所有时间字段 datetime，自动维护 create_time/update_time
--   * user_balance_flow 禁止物理删除（触发器硬保护 + 应用层不提供删除接口）
--   * 状态/枚举字段均为 tinyint，取值含义见各表注释
--   * 中奖统计表记录每一次中奖，退款不回滚计数
-- ============================================================================

CREATE DATABASE IF NOT EXISTS ibigou_blindbox
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ibigou_blindbox;

-- ---------------------------------------------------------------------------
-- 6.1 商家私有奖品池
-- prize_type: 1 折扣券, 2 立减券, 3 普通余额, 4 团购免单余额
-- limit_scope: 1 平台全局, 2 单商家, 3 单用户
-- limit_cycle: 1 每日, 2 每周, 3 不限周期
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS box_prize_pool (
  prize_id           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键自增',
  merchant_no        VARCHAR(64)  NOT NULL COMMENT '商家编号',
  prize_type         TINYINT UNSIGNED NOT NULL COMMENT '奖品类型 1折扣券 2立减券 3普通余额 4团购免单余额',
  prize_value        DECIMAL(10,2) NOT NULL COMMENT '优惠数值',
  weight             INT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '档位权重',
  enabled            TINYINT       NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  remark             VARCHAR(255)  DEFAULT NULL COMMENT '商家备注',
  is_put_public      TINYINT       NOT NULL DEFAULT 0 COMMENT '0不投放公共池 1投放公共池',
  is_support_ibigou  TINYINT       NOT NULL DEFAULT 0 COMMENT '0不支持宜必购 1支持宜必购渠道',
  limit_scope        TINYINT       NOT NULL DEFAULT 1 COMMENT '限额作用范围 1平台全局 2单商家 3单用户',
  limit_cycle        TINYINT       NOT NULL DEFAULT 1 COMMENT '限额周期 1每日 2每周 3不限周期',
  limit_max          INT           NOT NULL DEFAULT 0 COMMENT '最大中奖次数 0代表不限',
  create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (prize_id),
  KEY idx_prize_merchant (merchant_no),
  KEY idx_prize_enabled (merchant_no, enabled, prize_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家私有奖品池';

-- ---------------------------------------------------------------------------
-- 6.2 公共共享奖品池
-- 档位为私有池投放的副本；投放商家不可抽自己投放的公共奖品
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS box_public_pool (
  public_id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  source_merchant_no VARCHAR(64)  NOT NULL COMMENT '投放来源商家编号',
  prize_type         TINYINT      NOT NULL COMMENT '奖品类型 1折扣券 2立减券 3普通余额 4团购免单余额',
  prize_value        DECIMAL(10,2) NOT NULL COMMENT '优惠数值',
  weight             INT          NOT NULL DEFAULT 0 COMMENT '公共池档位权重',
  enabled            TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
  remark             VARCHAR(255) DEFAULT NULL COMMENT '备注',
  is_support_ibigou  TINYINT      NOT NULL DEFAULT 0 COMMENT '继承私有档位配置 0不支持 1支持',
  limit_scope        TINYINT      NOT NULL DEFAULT 1 COMMENT '限额作用范围',
  limit_cycle        TINYINT      NOT NULL DEFAULT 1 COMMENT '限额周期',
  limit_max          INT          NOT NULL DEFAULT 0 COMMENT '最大中奖次数 0不限',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (public_id),
  KEY idx_public_source (source_merchant_no),
  KEY idx_public_enabled (enabled, prize_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共共享奖品池';

-- ---------------------------------------------------------------------------
-- 6.3 merchant 商家门店表
-- box_discount_total_weight: 私有池折扣大类总权重
-- box_coupon_total_weight: 私有池立减余额大类总权重
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS merchant (
  merchant_no             VARCHAR(64)  NOT NULL COMMENT '主键商家编号',
  merchant_name           VARCHAR(128) NOT NULL COMMENT '商家门店名称',
  login_account           VARCHAR(64)  NOT NULL COMMENT '商家H5登录账号',
  login_pwd               VARCHAR(128) NOT NULL COMMENT '加密存储登录密码(BCrypt)',
  status                  TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  box_discount_total_weight INT        NOT NULL DEFAULT 0 COMMENT '私有池折扣大类总权重',
  box_coupon_total_weight   INT        NOT NULL DEFAULT 0 COMMENT '私有池立减余额大类总权重',
  private_pool_weight     INT          NOT NULL DEFAULT 0 COMMENT '第一层私有奖品池权重',
  public_pool_weight      INT          NOT NULL DEFAULT 0 COMMENT '第一层公共奖品池权重',
  member_expire_time      DATETIME     DEFAULT NULL COMMENT '商家会员过期时间(null=永久) 页面只读',
  create_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (merchant_no),
  UNIQUE KEY uk_merchant_account (login_account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家门店表';

-- ---------------------------------------------------------------------------
-- 6.4 盲盒中奖统计表（记录每一次中奖；退款不回滚中奖计数）
-- pool_type: 1私有池 2公共池 3美团专属池 4饿了么专属池
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS box_prize_limit_stat (
  stat_id       BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  merchant_no   VARCHAR(64) NOT NULL COMMENT '商家编号',
  user_phone    VARCHAR(20) NOT NULL COMMENT '用户手机号',
  prize_id      BIGINT      NOT NULL COMMENT '关联私有池id；公共奖品填public_id',
  pool_type     TINYINT     NOT NULL COMMENT '1私有池 2公共池 3美团专属池 4饿了么专属池 5抖音专属池',
  prize_type    TINYINT     NOT NULL COMMENT '奖品类型(冗余,便于限额统计)',
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '中奖时间',
  PRIMARY KEY (stat_id),
  KEY idx_stat_merchant_prize (merchant_no, prize_id, create_time),
  KEY idx_stat_user (user_phone, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盲盒中奖统计表';


-- ---------------------------------------------------------------------------
-- 6.5 用户优惠券表
-- status: 0未使用 1已核销 2已过期 3已作废
-- verify_type: 0未核销 1线下自动 2手工核销 3宜必购渠道核销
-- can_use_after_draw: 0暂不可用 1可用
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_coupon (
  coupon_id           BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  user_phone          VARCHAR(20)  NOT NULL COMMENT '用户手机号',
  source_merchant_no  VARCHAR(64)  NOT NULL COMMENT '发行商家编号',
  prize_type          TINYINT      NOT NULL COMMENT '奖品类型',
  prize_value         DECIMAL(10,2) NOT NULL COMMENT '优惠值',
  can_use_after_draw  TINYINT      NOT NULL DEFAULT 0 COMMENT '0暂不可用 1可用',
  is_support_ibigou   TINYINT      NOT NULL DEFAULT 0 COMMENT '0禁止宜必购 1允许宜必购渠道',
  status              TINYINT      NOT NULL DEFAULT 0 COMMENT '0未使用 1已核销 2已过期 3已作废',
  verify_type         TINYINT      NOT NULL DEFAULT 0 COMMENT '0未核销 1线下自动 2手工核销 3宜必购渠道核销',
  biz_no              VARCHAR(128) DEFAULT NULL COMMENT '关联业务单号(线下订单/宜必购订单号)',
  valid_start         DATETIME     NOT NULL COMMENT '生效时间',
  valid_end           DATETIME     NOT NULL COMMENT '过期时间',
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领券(中奖)时间',
  update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (coupon_id),
  KEY idx_coupon_user (user_phone, status),
  KEY idx_coupon_source (source_merchant_no, status),
  KEY idx_coupon_biz (biz_no),
  KEY idx_coupon_expire (valid_end, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- ---------------------------------------------------------------------------
-- 6.6 用户余额账户
-- total_balance: 账户总可用余额（含 can_use_after_draw=1 的流水累计）
-- version: 乐观锁字段，并发扣减保护
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_account (
  user_phone    VARCHAR(20)   NOT NULL COMMENT '主键手机号',
  total_balance DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '账户总可用余额',
  version       INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(并发扣减)',
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (user_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户余额账户';

-- ---------------------------------------------------------------------------
-- 6.7 余额流水表（禁止物理删除：触发器硬保护 + 应用层不提供删除接口）
-- flow_type: 1发放 2扣减 3退款退回
-- amount: 发放/退款为正数；扣减存负数
-- can_use_after_draw: 0暂不可用 1可用
-- source_pool_type: 1私有池 2公共池 3美团专属 4饿了么专属
-- verify_type: 0未消耗 1线下自动 2手工扣减 3宜必购渠道扣减
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_balance_flow (
  flow_id             BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  user_phone          VARCHAR(20)   NOT NULL COMMENT '用户手机号',
  flow_type           TINYINT       NOT NULL COMMENT '1发放 2扣减 3退款退回',
  amount              DECIMAL(12,2) NOT NULL COMMENT '金额：发放/退款正数；扣减负数',
  can_use_after_draw  TINYINT       NOT NULL DEFAULT 0 COMMENT '0暂不可用 1可用',
  source_pool_type    TINYINT       NOT NULL COMMENT '1私有池 2公共池 3美团专属 4饿了么专属 5抖音专属',
  verify_type         TINYINT       NOT NULL DEFAULT 0 COMMENT '0未消耗 1线下自动 2手工扣减 3宜必购渠道扣减',
  biz_no              VARCHAR(128)  DEFAULT NULL COMMENT '业务单号(线下订单/宜必购订单号)',
  remark              VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水生成时间',
  PRIMARY KEY (flow_id),
  KEY idx_flow_user (user_phone, create_time),
  KEY idx_flow_biz (biz_no),
  KEY idx_flow_pool (source_pool_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='余额流水表(禁止物理删除)';


-- ---------------------------------------------------------------------------
-- 6.8 第三方团购登记记录表（美团/饿了么）
-- channel: 1美团 2饿了么；qr_code_unique_key 识别二维码渠道
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS third_group_verify_record (
  record_id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  qr_code_unique_key VARCHAR(128) NOT NULL COMMENT '二维码唯一key，识别渠道',
  user_phone         VARCHAR(20)  NOT NULL COMMENT '用户手机号',
  channel            TINYINT      NOT NULL COMMENT '1美团 2饿了么 3抖音',
  draw_batch_no      VARCHAR(64)  DEFAULT NULL COMMENT '抽奖批次号(折算防重, YBG-C-RETURN-001)',
  return_balance     DECIMAL(10,2) DEFAULT 0.00 COMMENT '登记折算赠送余额(YBG-C-RETURN-001)',
  return_desc        VARCHAR(255) DEFAULT NULL COMMENT '折算赠送说明(YBG-C-RETURN-001)',
  group_amount       DECIMAL(10,2) NOT NULL COMMENT '用户录入团购消费金额',
  prize_info         TEXT         COMMENT '本次中奖奖品json摘要',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
  PRIMARY KEY (record_id),
  KEY idx_group_qr (qr_code_unique_key, create_time),
  KEY idx_group_user (user_phone, create_time),
  KEY idx_group_channel (channel, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方团购登记记录表';


-- ---------------------------------------------------------------------------
-- 6.9 系统全局配置表
-- key 清单：
--   balance_deduct_rate   余额抵扣百分比 0-100（0=全平台关闭余额抵扣）
--   ibigou_channel_switch 宜必购渠道总开关 0关闭 1开启
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_global_config (
  config_key   VARCHAR(64)  NOT NULL COMMENT '配置主键key',
  config_value VARCHAR(255) NOT NULL COMMENT '配置值',
  remark       VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统全局配置表';

-- ---------------------------------------------------------------------------
-- 6.10 宜必购内部订单表
-- refund_status: 0未退款 1全额退款 2部分退款
-- merchant_no: 消费归属商家（对账用，本单消耗资产发行商家）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ibigou_order (
  ibigou_order_no VARCHAR(64)   NOT NULL COMMENT '主键，宜必购订单biz_no',
  user_phone      VARCHAR(20)   NOT NULL COMMENT '用户手机号',
  merchant_no     VARCHAR(64)   NOT NULL COMMENT '消费归属商家(对账用)',
  coupon_id       BIGINT UNSIGNED DEFAULT NULL COMMENT 'null未使用券',
  deduct_balance  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '本单扣减余额金额',
  order_amount    DECIMAL(12,2) NOT NULL COMMENT '订单原始金额',
  pay_amount      DECIMAL(12,2) NOT NULL COMMENT '实付金额',
  refund_status   TINYINT       NOT NULL DEFAULT 0 COMMENT '0未退款 1全额退款 2部分退款',
  refund_amount   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已经退款金额',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  pay_time        DATETIME      DEFAULT NULL COMMENT '支付时间',
  refund_time     DATETIME      DEFAULT NULL COMMENT '退款时间',
  update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (ibigou_order_no),
  KEY idx_ibigou_user (user_phone, create_time),
  KEY idx_ibigou_merchant (merchant_no, create_time),
  KEY idx_ibigou_refund (refund_status, refund_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宜必购内部订单表';

-- ---------------------------------------------------------------------------
-- 流水表禁止物理删除硬保护
-- ---------------------------------------------------------------------------
DELIMITER $$
DROP TRIGGER IF EXISTS trg_balance_flow_no_delete$$
CREATE TRIGGER trg_balance_flow_no_delete
BEFORE DELETE ON user_balance_flow
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user_balance_flow 禁止物理删除(需求V1.4 第7章)';
END$$
DELIMITER ;

-- ---------------------------------------------------------------------------
-- 初始数据：全局配置
-- ---------------------------------------------------------------------------
INSERT INTO sys_global_config (config_key, config_value, remark) VALUES
  ('balance_deduct_rate',   '80', '余额最大抵扣百分比 0-100；0=全平台关闭余额抵扣(线下+宜必购同时生效)'),
  ('ibigou_channel_switch', '1',  '宜必购渠道总开关 0关闭 1开启；关闭则所有端隐藏入口并拒绝下单')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- ============================================================================
-- V1.4 补充设计（文档第 6 章未列，但业务流程明确要求的表/字段）
--   1) offline_order         流程 E 线下门店自营订单（退款需要订单载体）
--   2) box_group_prize_pool  5.2.7 美团/饿了么专属盲盒独立奖品池（与私有/公共池不互通）
--   3) ibigou_goods          5.1.5 宜必购渠道商品浏览/下单
--   4) user_coupon.draw_batch_no / user_balance_flow.draw_batch_no
--                            流程闭环：按批次把本次盲盒产出全部资产置 can_use_after_draw=1
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 补充表 A：线下门店自营订单（offline_order）
-- 结构同 ibigou_order；biz_no 贯穿 user_coupon / user_balance_flow
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS offline_order (
  offline_order_no VARCHAR(64)   NOT NULL COMMENT '主键，线下订单biz_no',
  user_phone       VARCHAR(20)   NOT NULL COMMENT '用户手机号',
  merchant_no      VARCHAR(64)   NOT NULL COMMENT '消费商家编号(核销券须为发行商家)',
  coupon_id        BIGINT UNSIGNED DEFAULT NULL COMMENT 'null未使用券',
  deduct_balance   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '本单扣减余额金额',
  order_amount     DECIMAL(12,2) NOT NULL COMMENT '订单原始金额',
  pay_amount       DECIMAL(12,2) NOT NULL COMMENT '实付金额',
  refund_status    TINYINT       NOT NULL DEFAULT 0 COMMENT '0未退款 1全额退款 2部分退款',
  refund_amount    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已经退款金额',
  create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  pay_time         DATETIME      DEFAULT NULL COMMENT '支付时间',
  refund_time      DATETIME      DEFAULT NULL COMMENT '退款时间',
  update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (offline_order_no),
  KEY idx_offline_user (user_phone, create_time),
  KEY idx_offline_merchant (merchant_no, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线下门店自营订单表(V1.4补充,支撑流程E)';

-- ---------------------------------------------------------------------------
-- 补充表 B：美团/饿了么专属盲盒独立奖品池（box_group_prize_pool）
-- channel: 1美团 2饿了么；两套盲盒独立配置，不和本店普通盲盒/公共池互通
-- prize_type 同 box_prize_pool；当前按档位权重直接随机开奖
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS box_group_prize_pool (
  group_pool_id      BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  merchant_no        VARCHAR(64)  NOT NULL COMMENT '商家编号',
  channel            TINYINT      NOT NULL COMMENT '1美团 2饿了么 3抖音',
  prize_type         TINYINT      NOT NULL COMMENT '1折扣券 2立减券 3普通余额 4团购免单余额',
  prize_value        DECIMAL(10,2) NOT NULL COMMENT '优惠数值',
  weight             INT          NOT NULL DEFAULT 0 COMMENT '档位权重',
  enabled            TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  is_support_ibigou  TINYINT      NOT NULL DEFAULT 0 COMMENT '0不支持宜必购 1支持宜必购渠道',
  limit_scope        TINYINT      NOT NULL DEFAULT 1 COMMENT '限额作用范围',
  limit_cycle        TINYINT      NOT NULL DEFAULT 1 COMMENT '限额周期',
  limit_max          INT          NOT NULL DEFAULT 0 COMMENT '最大中奖次数 0不限',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (group_pool_id),
  KEY idx_group_pool (merchant_no, channel, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购专属盲盒奖品池(V1.4补充,独立于私有/公共池)';


-- ---------------------------------------------------------------------------
-- 补充表 C：宜必购渠道商品（ibigou_goods）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ibigou_goods (
  goods_id    BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  goods_name  VARCHAR(128)   NOT NULL COMMENT '商品名称',
  price       DECIMAL(12,2)  NOT NULL COMMENT '商品价格(下单原始金额来源)',
  enabled     TINYINT        NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
  create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (goods_id),
  KEY idx_goods_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宜必购渠道商品表(V1.4补充,支撑G流程)';

-- ---------------------------------------------------------------------------
-- ---------------------------------------------------------------------------
-- 补充字段：抽奖批次号（流程闭环按批次更新 can_use_after_draw）
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用存储过程做幂等判断
-- ---------------------------------------------------------------------------
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_add_draw_batch_no$$
CREATE PROCEDURE sp_add_draw_batch_no()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_coupon' AND COLUMN_NAME = 'draw_batch_no') THEN
    ALTER TABLE user_coupon ADD COLUMN draw_batch_no VARCHAR(64) DEFAULT NULL COMMENT '抽奖批次号(闭环按批次置can_use_after_draw=1)';
    ALTER TABLE user_coupon ADD KEY idx_coupon_batch (draw_batch_no);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_balance_flow' AND COLUMN_NAME = 'draw_batch_no') THEN
    ALTER TABLE user_balance_flow ADD COLUMN draw_batch_no VARCHAR(64) DEFAULT NULL COMMENT '抽奖批次号(闭环按批次置can_use_after_draw=1)';
    ALTER TABLE user_balance_flow ADD KEY idx_flow_batch (draw_batch_no);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_balance_flow' AND COLUMN_NAME = 'merchant_no') THEN
    ALTER TABLE user_balance_flow ADD COLUMN merchant_no VARCHAR(64) DEFAULT NULL COMMENT '抽奖门店/消费门店(报表对账用,V1.4补充)';
    ALTER TABLE user_balance_flow ADD KEY idx_flow_merchant (merchant_no, create_time);
  END IF;
END$$
DELIMITER ;
CALL sp_add_draw_batch_no();
DROP PROCEDURE sp_add_draw_batch_no;

-- ============================================================================
-- V1.4 补充设计（鉴权完整化）：商家会话 + 平台管理员
--   1) merchant_session  商家登录会话（DB 持久化，重启不失效，token 过期清理）
--   2) admin_user        软件公司平台管理员账号（5.3 平台登录）
-- ============================================================================

-- 补充表 D：商家登录会话
CREATE TABLE IF NOT EXISTS merchant_session (
  token        VARCHAR(64)  NOT NULL COMMENT '主键，登录 token',
  merchant_no  VARCHAR(64)  NOT NULL COMMENT '商家编号',
  expire_time  DATETIME     NOT NULL COMMENT '过期时间',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (token),
  KEY idx_session_merchant (merchant_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家登录会话表(V1.4补充,鉴权持久化)';

-- 补充表 E：平台管理员账号
CREATE TABLE IF NOT EXISTS admin_user (
  admin_id    BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  account     VARCHAR(64)  NOT NULL COMMENT '登录账号',
  login_pwd   VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (admin_id),
  UNIQUE KEY uk_admin_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台管理员账号表(V1.4补充,支撑平台登录)';

-- 平台管理员初始账号由 AdminService 启动时用 BCrypt 真实加密创建（admin / Admin@2026）


-- ============================================================================
-- V1.5 补充（用户拍板）：美团/饿了么专属盲盒大类权重
--   box_group_pool_config  每商家每渠道的大类权重（折扣 vs 立减&余额）
-- ============================================================================
CREATE TABLE IF NOT EXISTS box_group_pool_config (
  id                        BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  merchant_no               VARCHAR(64) NOT NULL COMMENT '商家编号',
  channel                   TINYINT     NOT NULL COMMENT '1美团 2饿了么',
  discount_total_weight     INT         NOT NULL DEFAULT 0 COMMENT '折扣大类总权重',
  coupon_balance_total_weight INT       NOT NULL DEFAULT 0 COMMENT '立减&余额大类总权重',
  create_time               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_pool_config (merchant_no, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购专属盲盒大类权重(V1.5补充)';

-- ============================================================================
-- V1.5 补充（用户最终定稿）：门店级抵扣配置 + 单日抵扣额度
--   1) merchant 扩展：balance_deduct_percent / daily_deduct_limit / receive_mode / receive_qr_img
--   2) daily_deduct_quota  本店单人单日余额抵扣累计（每日 0 点按日期维度自然归零）
-- ============================================================================

-- merchant 扩展字段（幂等添加）
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_add_merchant_v15_fields$$
CREATE PROCEDURE sp_add_merchant_v15_fields()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant' AND COLUMN_NAME = 'balance_deduct_percent') THEN
    ALTER TABLE merchant ADD COLUMN balance_deduct_percent INT DEFAULT NULL COMMENT '本店余额抵扣百分比 0-100(null=用平台全局rate, 0=本店禁止余额抵扣)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant' AND COLUMN_NAME = 'daily_deduct_limit') THEN
    ALTER TABLE merchant ADD COLUMN daily_deduct_limit DECIMAL(12,2) DEFAULT NULL COMMENT '本店单人单日余额抵扣总上限(null/0=不限制)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant' AND COLUMN_NAME = 'receive_mode') THEN
    ALTER TABLE merchant ADD COLUMN receive_mode TINYINT DEFAULT 1 COMMENT '收款模式 1模式A(上传收款码) 2模式B(扫码枪核验)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant' AND COLUMN_NAME = 'receive_qr_img') THEN
    ALTER TABLE merchant ADD COLUMN receive_qr_img VARCHAR(255) DEFAULT NULL COMMENT '模式A营业收款码图片URL';
  END IF;
END$$
DELIMITER ;
CALL sp_add_merchant_v15_fields();
DROP PROCEDURE sp_add_merchant_v15_fields;

-- 补充表 G：本店单人单日余额抵扣额度
CREATE TABLE IF NOT EXISTS daily_deduct_quota (
  quota_id     BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  user_phone   VARCHAR(20)   NOT NULL COMMENT '用户手机号',
  merchant_no  VARCHAR(64)   NOT NULL COMMENT '门店编号',
  deduct_date  DATE          NOT NULL COMMENT '抵扣日期(按日期天然清零)',
  accum_deduct DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '今日累计抵扣金额',
  update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (quota_id),
  UNIQUE KEY uk_quota (user_phone, merchant_no, deduct_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本店单人单日余额抵扣额度(V1.5补充)';

-- ============================================================================
-- V1.5 补充（会员续费体系）：续费订单 + 审计日志 + 全局套餐配置
--   1) member_order      商家续费订单（购买/赠送/有效期叠加）
--   2) audit_log         人工操作审计（商家兜底/退款/平台调整有效期）
--   3) sys_global_config 新增 key：free_trial_days/monthly_price/renew_gift_switch/renew_gift_months
-- ============================================================================

-- 补充表 H：商家续费订单
CREATE TABLE IF NOT EXISTS member_order (
  order_no     VARCHAR(64)   NOT NULL COMMENT '主键，续费订单号',
  merchant_no  VARCHAR(64)   NOT NULL COMMENT '商家编号',
  amount       DECIMAL(12,2) NOT NULL COMMENT '支付金额(年费=月单价×12)',
  buy_months   INT           NOT NULL COMMENT '购买时长(12)',
  gift_months  INT           NOT NULL DEFAULT 0 COMMENT '活动赠送时长(0=不送)',
  total_months INT           NOT NULL COMMENT '总增加时长=购买+赠送',
  status       TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消',
  valid_from   DATETIME      DEFAULT NULL COMMENT '本次增加有效期的起(支付确认后计算)',
  valid_to     DATETIME      DEFAULT NULL COMMENT '本次增加有效期的止',
  pay_time     DATETIME      DEFAULT NULL COMMENT '支付时间',
  create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (order_no),
  KEY idx_member_order_merchant (merchant_no, create_time),
  KEY idx_member_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家续费订单(V1.5补充)';

-- 补充表 I：人工操作审计日志
CREATE TABLE IF NOT EXISTS audit_log (
  log_id       BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  merchant_no  VARCHAR(64)   DEFAULT NULL COMMENT '门店编号',
  operator     VARCHAR(64)   NOT NULL COMMENT '操作账号(商家账号/平台admin)',
  action       VARCHAR(64)   NOT NULL COMMENT '操作类型: manual_verify/manual_complete/refund/adjust_expire等',
  user_phone   VARCHAR(20)   DEFAULT NULL COMMENT '关联用户手机号',
  amount       DECIMAL(12,2) DEFAULT NULL COMMENT '涉及金额',
  detail       VARCHAR(500)  DEFAULT NULL COMMENT '操作详情',
  create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (log_id),
  KEY idx_audit_merchant (merchant_no, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工操作审计日志(V1.5补充)';

-- 全局套餐配置初始值（代码不写死，平台后台可改）
INSERT INTO sys_global_config (config_key, config_value, remark) VALUES
  ('free_trial_days',     '30',  '商家免费试用天数(激活当天起计时)'),
  ('monthly_price',       '150', '会员每月单价(元)，年费=月单价×12'),
  ('renew_gift_switch',   '0',   '年费赠送活动开关 0关闭 1开启'),
  ('renew_gift_months',   '0',   '缴满12个月年费赠送月数(0=不送)'),
  ('wx_pay_mch_id',       '',    '平台微信支付商户号(仅平台后台可见,脱敏)'),
  ('wx_pay_app_id',       '',    '平台微信支付AppID(脱敏)'),
  ('wx_pay_api_key',      '',    '平台微信支付API密钥(脱敏)')
ON DUPLICATE KEY UPDATE config_key = config_key;

-- merchant 收款码字段（微信/支付宝两张，模式A用）
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_add_merchant_qr_fields$$
CREATE PROCEDURE sp_add_merchant_qr_fields()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant' AND COLUMN_NAME = 'receive_qr_img_wechat') THEN
    ALTER TABLE merchant ADD COLUMN receive_qr_img_wechat VARCHAR(255) DEFAULT NULL COMMENT '模式A微信收款码图片URL';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant' AND COLUMN_NAME = 'receive_qr_img_alipay') THEN
    ALTER TABLE merchant ADD COLUMN receive_qr_img_alipay VARCHAR(255) DEFAULT NULL COMMENT '模式A支付宝收款码图片URL';
  END IF;
END$$
DELIMITER ;
CALL sp_add_merchant_qr_fields();
DROP PROCEDURE sp_add_merchant_qr_fields;

-- V1.5 细化（27行补充）：offline_order 实付金额/跨店返还 + 全局返还比例
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_add_offline_order_v15_fields$$
CREATE PROCEDURE sp_add_offline_order_v15_fields()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offline_order' AND COLUMN_NAME = 'paid_amount') THEN
    ALTER TABLE offline_order ADD COLUMN paid_amount DECIMAL(12,2) DEFAULT NULL COMMENT '用户实际付金额(H5回填,对账用)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offline_order' AND COLUMN_NAME = 'return_balance') THEN
    ALTER TABLE offline_order ADD COLUMN return_balance DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '本单跨店返还余额(实付×比例)';
  END IF;
END$$
DELIMITER ;
CALL sp_add_offline_order_v15_fields();
DROP PROCEDURE sp_add_offline_order_v15_fields;

INSERT INTO sys_global_config (config_key, config_value, remark) VALUES
  ('cross_store_return_percent', '5', '跨店返还余额比例(%)：订单闭环按实付×比例发放；商家只读不可改')
ON DUPLICATE KEY UPDATE config_key = config_key;

-- V1.5 模式B：offline_order 凭证与状态
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_add_offline_order_modeB_fields$$
CREATE PROCEDURE sp_add_offline_order_modeB_fields()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offline_order' AND COLUMN_NAME = 'order_status') THEN
    ALTER TABLE offline_order ADD COLUMN order_status TINYINT NOT NULL DEFAULT 1 COMMENT '0待确认(模式B挂起) 1已完成 2已取消 3已退款';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offline_order' AND COLUMN_NAME = 'voucher_token') THEN
    ALTER TABLE offline_order ADD COLUMN voucher_token VARCHAR(64) DEFAULT NULL COMMENT '模式B一次性订单凭证token(确认完成后失效)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offline_order' AND COLUMN_NAME = 'voucher_expire') THEN
    ALTER TABLE offline_order ADD COLUMN voucher_expire DATETIME DEFAULT NULL COMMENT '凭证过期时间';
  END IF;
END$$
DELIMITER ;
CALL sp_add_offline_order_modeB_fields();
DROP PROCEDURE sp_add_offline_order_modeB_fields;

-- V1.5 审计撤销：audit_log 扩展
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_add_audit_revoke_fields$$
CREATE PROCEDURE sp_add_audit_revoke_fields()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log' AND COLUMN_NAME = 'ref_id') THEN
    ALTER TABLE audit_log ADD COLUMN ref_id VARCHAR(64) DEFAULT NULL COMMENT '关联ID(券coupon_id/订单号/商家号)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log' AND COLUMN_NAME = 'revoke_type') THEN
    ALTER TABLE audit_log ADD COLUMN revoke_type VARCHAR(32) DEFAULT NULL COMMENT '撤销类型: COUPON_RESTORE/BALANCE_RETURN/REFUND_REVERSE/EXPIRE_DECREASE/NONE';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log' AND COLUMN_NAME = 'revoked') THEN
    ALTER TABLE audit_log ADD COLUMN revoked TINYINT NOT NULL DEFAULT 0 COMMENT '0未撤销 1已撤销';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log' AND COLUMN_NAME = 'revoke_time') THEN
    ALTER TABLE audit_log ADD COLUMN revoke_time DATETIME DEFAULT NULL COMMENT '撤销时间';
  END IF;
END$$
DELIMITER ;
CALL sp_add_audit_revoke_fields();
DROP PROCEDURE sp_add_audit_revoke_fields;

-- V1.5 平台微信支付扩展配置
INSERT INTO sys_global_config (config_key, config_value, remark) VALUES
  ('wx_pay_enabled',     '0',   '平台微信支付开关 0关闭(测试用mock确认) 1开启(真实微信Native)'),
  ('wx_pay_api_v3_key',  '',    '平台微信支付API v3密钥(仅平台后台可见,脱敏)'),
  ('wx_pay_cert_path',   '',    '平台微信商户证书路径(apiclient_cert.pem)'),
  ('wx_pay_cert_serial', '',    '平台微信商户证书序列号')
ON DUPLICATE KEY UPDATE config_key = config_key;

-- ============================================================================
-- V1.5 P2：站内消息（到期提醒）+ 订单交易流水号
--   1) merchant_message    商家站内消息（会员到期提醒 7/3/1 天等）
--   2) offline_order.merchant_trade_no  商家微信/支付宝交易流水号（退款去商户后台用）
-- ============================================================================
CREATE TABLE IF NOT EXISTS merchant_message (
  id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  merchant_no VARCHAR(64) NOT NULL COMMENT '商家编号',
  msg_type    VARCHAR(32) NOT NULL COMMENT '类型: expire_remind/order/other',
  title       VARCHAR(128) NOT NULL COMMENT '标题',
  content     VARCHAR(500) DEFAULT NULL COMMENT '内容',
  is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_msg_merchant (merchant_no, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家站内消息(V1.5 P2)';

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_add_offline_trade_no$$
CREATE PROCEDURE sp_add_offline_trade_no()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offline_order' AND COLUMN_NAME = 'merchant_trade_no') THEN
    ALTER TABLE offline_order ADD COLUMN merchant_trade_no VARCHAR(64) DEFAULT NULL COMMENT '商家微信/支付宝交易流水号(退款时去商户后台办理)';
  END IF;
END$$
DELIMITER ;
CALL sp_add_offline_trade_no();
DROP PROCEDURE sp_add_offline_trade_no;

-- ============================================================================
-- V1.5 P2：蓝牙音箱/语音播报事件
--   announce_log  商家播报事件（开奖/订单完成/退款/兜底话术；商家H5轮询朗读）
-- ============================================================================
CREATE TABLE IF NOT EXISTS announce_log (
  announce_id BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  merchant_no VARCHAR(64) NOT NULL COMMENT '商家编号',
  event_type  VARCHAR(32) NOT NULL COMMENT 'draw/order_auto/order_manual/refund/manual_complete',
  content     VARCHAR(500) NOT NULL COMMENT '播报话术',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  PRIMARY KEY (announce_id),
  KEY idx_announce_merchant (merchant_no, announce_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家播报事件(V1.5 P2)';

-- ============================================================================
-- P0 安全加固（专业评审）：
--   1) customer_session   顾客登录会话（短信验证码，24h）
--   2) offline_order.paid_diff  实付差异（实付≠应付异常标识）
--   3) merchant.receive_qr_status 收款码审核状态
--   4) sys_global_config.test_pay_confirm_enabled 测试确认支付开关
-- ============================================================================
CREATE TABLE IF NOT EXISTS customer_session (
  token       VARCHAR(64) NOT NULL COMMENT '主键，登录 token',
  user_phone  VARCHAR(20) NOT NULL COMMENT '用户手机号',
  expire_time DATETIME    NOT NULL COMMENT '过期时间',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (token),
  KEY idx_customer_session_phone (user_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾客登录会话(P0安全)';

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_add_p0_fields$$
CREATE PROCEDURE sp_add_p0_fields()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offline_order' AND COLUMN_NAME = 'paid_diff') THEN
    ALTER TABLE offline_order ADD COLUMN paid_diff DECIMAL(12,2) DEFAULT NULL COMMENT '实付差异(实际付-应付; 非0为异常单)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant' AND COLUMN_NAME = 'receive_qr_status') THEN
    ALTER TABLE merchant ADD COLUMN receive_qr_status TINYINT NOT NULL DEFAULT 0 COMMENT '收款码审核 0未上传 1待审核 2通过 3驳回';
  END IF;
END$$
DELIMITER ;
CALL sp_add_p0_fields();
DROP PROCEDURE sp_add_p0_fields;

INSERT INTO sys_global_config (config_key, config_value, remark) VALUES
  ('test_pay_confirm_enabled', '0', '测试确认支付开关 0关闭(生产仅走微信回调) 1开启(仅测试环境)')
ON DUPLICATE KEY UPDATE config_key = config_key;

-- ============================================================================
-- P1 业务完整性：
--   1) user_message        用户端站内消息（跨店返还/退款触达）
--   2) identity_qr_secret  长期身份二维码 HMAC 密钥
--   3) coupon_valid_days   券默认有效期（天，可配置）
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_message (
  id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  user_phone  VARCHAR(20) NOT NULL COMMENT '用户手机号',
  msg_type    VARCHAR(32) NOT NULL COMMENT 'return_balance/refund/other',
  title       VARCHAR(128) NOT NULL COMMENT '标题',
  content     VARCHAR(500) DEFAULT NULL COMMENT '内容',
  is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_user_msg (user_phone, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户站内消息(P1)';

INSERT INTO sys_global_config (config_key, config_value, remark) VALUES
  ('identity_qr_secret', '', '长期身份二维码HMAC签名密钥(留空=自动生成)'),
  ('coupon_valid_days',  '30', '优惠券默认有效期(天)')
ON DUPLICATE KEY UPDATE config_key = config_key;

-- ============================================================================
-- P2 运营容灾：
--   1) sys_alert  系统告警（异常/任务失败；平台后台查看处理）
-- ============================================================================
CREATE TABLE IF NOT EXISTS sys_alert (
  alert_id    BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
  alert_type  VARCHAR(32) NOT NULL COMMENT 'exception/job/payment/other',
  content     VARCHAR(500) NOT NULL COMMENT '告警内容',
  status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0未处理 1已处理',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
  PRIMARY KEY (alert_id),
  KEY idx_alert_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统告警(P2)';
