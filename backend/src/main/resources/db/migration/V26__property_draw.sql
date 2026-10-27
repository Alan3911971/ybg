-- V26 物业抽盒记录（业主端 P1：㉕抽盲盒 / ㉖开盒结果 / ㉔缴费抵扣 / ㉓我的）
-- 用途：算剩余次数、本期第几位、券号、今日已抽、概率公开
CREATE TABLE IF NOT EXISTS `property_draw_record` (
  `record_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `company_id`  BIGINT       NOT NULL                COMMENT '物业公司的 company_id',
  `owner_id`    BIGINT       NULL                    COMMENT '业主 ID',
  `phone`       VARCHAR(32)  NOT NULL                COMMENT '抽奖手机号',
  `prize_id`    BIGINT       NULL                    COMMENT '命中的 property_prize_pool.prize_id',
  `prize_type`  INT          NOT NULL DEFAULT 0      COMMENT '0普通 1折扣 2立减 3余额 4团购免单',
  `prize_value` DECIMAL(10,2) NOT NULL DEFAULT 0.00  COMMENT '奖品面值',
  `coupon_no`   VARCHAR(40)  NULL                    COMMENT '券号，如 BZ-20261027-0088',
  `coupon_id`   BIGINT       NULL                    COMMENT '关联的 user_coupon.coupon_id',
  `wallet_before` DECIMAL(10,2) NULL                 COMMENT '抽奖前物业钱包余额',
  `wallet_after`  DECIMAL(10,2) NULL                 COMMENT '抽奖后物业钱包余额',
  `seq`        INT          NOT NULL DEFAULT 0      COMMENT '本期第几位（按公司累计）',
  `draw_date`  DATE         NOT NULL                COMMENT '抽奖日期',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`record_id`),
  KEY `idx_pdr_company` (`company_id`),
  KEY `idx_pdr_phone` (`phone`),
  KEY `idx_pdr_date` (`draw_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物业抽盒记录';

-- 抽奖 / 分账「入账」类流水没有对应账单（钱进钱包，抵扣时才关联账单），bill_id 允许为空
ALTER TABLE `property_deduct_log` MODIFY `bill_id` BIGINT(20) NULL COMMENT '关联账单；入账类流水可为空';
