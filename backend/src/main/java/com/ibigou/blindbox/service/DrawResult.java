package com.ibigou.blindbox.service;

import java.math.BigDecimal;

/**
 * 开奖结果。
 *
 * @param drawBatchNo 抽奖批次号（流程闭环按批次置 can_use_after_draw=1）
 * @param prizeType   奖品类型 1折扣券 2立减券 3普通余额 4团购免单余额
 * @param prizeValue  优惠数值
 * @param isCoupon    是否优惠券（true 发券 / false 发余额）
 */
public record DrawResult(String drawBatchNo, int prizeType, BigDecimal prizeValue, boolean isCoupon) {
}
