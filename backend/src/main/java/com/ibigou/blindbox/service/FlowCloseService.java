package com.ibigou.blindbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import com.ibigou.blindbox.repository.UserCouponRepository;

/**
 * 流程闭环服务（V1.4 第 7 章）：
 * 触发条件满足任意一条，本次盲盒产出全部资产置 can_use_after_draw=1：
 * ①本店自营下单支付成功；②第三方团购登记记录保存成功；③用户关闭 H5 会话（由定时补偿任务兜底）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowCloseService {

    private final UserCouponRepository couponRepository;
    private final BalanceService balanceService;

    /** 按批次闭环：全部券 + 全部余额流水置可用 */
    @Transactional
    public void closeByDrawBatch(String drawBatchNo) {
        if (drawBatchNo == null || drawBatchNo.isBlank()) {
            return;
        }
        int coupons = couponRepository.activateDrawBatch(drawBatchNo, LocalDateTime.now());
        balanceService.activateDrawBatch(drawBatchNo);
        log.info("流程闭环完成 batch={} coupons={}", drawBatchNo, coupons);
    }
}
