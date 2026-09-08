package com.ibigou.blindbox.service;

import com.ibigou.blindbox.repository.UserBalanceFlowRepository;
import com.ibigou.blindbox.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 定时任务（V1.4 第 11 章）：
 * ①过期优惠券标记 status=2；②补偿任务扫描修复 can_use_after_draw 异常状态
 * （抽奖后用户直接关闭 H5 会话未闭环的批次，由补偿任务兜底闭环）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    /** 会话闭环兜底窗口：抽奖后超过该分钟数未闭环，视为用户已离开页面 */
    private static final long CLOSE_SESSION_WINDOW_MINUTES = 10;

    private final UserCouponRepository couponRepository;
    private final UserBalanceFlowRepository flowRepository;
    private final FlowCloseService flowCloseService;
    private final MessageService messageService;
    private final AlertService alertService;
    private final com.ibigou.blindbox.service.WxPayService wxPayService;
    private final com.ibigou.blindbox.service.MemberService memberService;
    private final com.ibigou.blindbox.repository.MemberOrderRepository memberOrderRepository;
    private final com.ibigou.blindbox.repository.AnnounceLogRepository announceLogRepository;
    private final com.ibigou.blindbox.repository.MerchantMessageRepository merchantMessageRepository;
    private final com.ibigou.blindbox.repository.UserMessageRepository userMessageRepository;
    private final com.ibigou.blindbox.repository.AuditLogRepository auditLogRepository;
    private final com.ibigou.blindbox.repository.MemberAppointmentRepository memberAppointmentRepository;

    /** 过期券标记：每小时执行 */
    @Scheduled(cron = "0 5 * * * ?")
    @Transactional
    public void expireCoupons() {
        int n = couponRepository.markExpired(LocalDateTime.now());
        if (n > 0) {
            log.info("定时任务：过期优惠券标记完成 count={}", n);
        }
    }

    /** 会员到期提醒：每天 9:30 生成 7/3/1 天前提醒（幂等） */
    @Scheduled(cron = "0 30 9 * * ?")
    public void expireRemind() {
        messageService.generateExpireReminders();
    }

    /** 补偿任务：每 5 分钟扫描未闭环批次（模拟关闭 H5 会话触发闭环） */
    @Scheduled(cron = "0 */5 * * * ?")
    public void compensateDrawBatch() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(CLOSE_SESSION_WINDOW_MINUTES);
        Set<String> batches = new LinkedHashSet<>();
        batches.addAll(couponRepository.findPendingBatches(before));
        batches.addAll(flowRepository.findPendingBatches(before));
        for (String batchNo : batches) {
            try {
                flowCloseService.closeByDrawBatch(batchNo);
                log.info("补偿任务：批次闭环完成 batch={}", batchNo);
            } catch (Exception e) {
                log.error("补偿任务失败 batch={}", batchNo, e);
                alertService.record("job", "补偿任务失败 batch=" + batchNo + ": " + e.getMessage());
            }
        }
    }

    /** P2-12 微信补单：待支付超时订单查单（每 5 分钟） */
    @Scheduled(cron = "0 */5 * * * ?")
    public void payReconciler() {
        try {
            if (!wxPayService.enabled()) {
                return; // 测试模式走人工确认
            }
            java.time.LocalDateTime before = java.time.LocalDateTime.now().minusMinutes(15);
            for (com.ibigou.blindbox.entity.MemberOrder order
                    : memberOrderRepository.findByStatusAndCreateTimeBefore(0, before)) {
                try {
                    String state = wxPayService.queryOrderState(order.getOrderNo());
                    if ("SUCCESS".equals(state)) {
                        memberService.confirmRenewPaid(order.getOrderNo(), "pay-reconciler");
                        log.info("补单确认支付 order={}", order.getOrderNo());
                    } else if ("CLOSED".equals(state) || "REVOKED".equals(state) || "NOTPAY".equals(state)) {
                        order.setStatus(2);
                        order.setUpdateTime(java.time.LocalDateTime.now());
                        memberOrderRepository.save(order);
                        log.info("补单取消订单 order={}", order.getOrderNo());
                    }
                } catch (Exception e) {
                    alertService.record("payment", "补单查询失败 order=" + order.getOrderNo() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("补单任务异常", e);
            alertService.record("payment", "补单任务异常: " + e.getMessage());
        }
    }

    /** P2-15 归档清理：每日凌晨删除过期日志/消息 */
    @Scheduled(cron = "0 20 3 * * ?")
    public void archiveClean() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        announceLogRepository.deleteByCreateTimeBefore(now.minusDays(30));   // 播报保留 30 天
        merchantMessageRepository.deleteByCreateTimeBefore(now.minusDays(90)); // 商家消息保留 90 天
        userMessageRepository.deleteByCreateTimeBefore(now.minusDays(90));    // 用户消息保留 90 天
        auditLogRepository.deleteByCreateTimeBefore(now.minusDays(180));      // 审计保留 180 天
        log.info("归档清理完成");
    }

    /** 预约提醒：每 5 分钟扫描即将到点的预约，写商家消息（幂等 remind_sent） */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    public void appointmentRemind() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.util.List<com.ibigou.blindbox.entity.MemberAppointment> list =
                memberAppointmentRepository.findPendingRemind(now, now.plusMinutes(60));
        for (com.ibigou.blindbox.entity.MemberAppointment a : list) {
            try {
                long mins = java.time.Duration.between(now, a.getApptTime()).toMinutes();
                String phone = a.getUserPhone() == null ? "" : a.getUserPhone();
                String masked = phone.length() >= 7 ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4) : phone;
                com.ibigou.blindbox.entity.MerchantMessage msg = new com.ibigou.blindbox.entity.MerchantMessage();
                msg.setMerchantNo(a.getMerchantNo());
                msg.setMsgType("appointment_remind");
                msg.setTitle("⏰ 预约提醒");
                msg.setContent(masked + " 预约了" + (a.getServiceItem() == null ? "服务" : a.getServiceItem())
                        + "，将于 " + a.getApptTime().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                        + " 开始" + (mins <= 0 ? "（已到时间）" : "（还有" + mins + "分钟）"));
                msg.setIsRead(0);
                msg.setCreateTime(now);
                merchantMessageRepository.save(msg);
                memberAppointmentRepository.markRemindSent(a.getId());
                log.info("预约提醒已发送 merchant={} appt={}", a.getMerchantNo(), a.getId());
            } catch (Exception e) {
                log.warn("预约提醒失败 id={} err={}", a.getId(), e.getMessage());
            }
        }
    }
}
