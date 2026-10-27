package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.enums.PrizeType;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * V19 会员 CRM 增强服务：预约回复 / 生日提醒 / 流失预警。
 *
 * <p><b>触达通道说明（重要）：</b>目前给顾客的真实可达通道是「用户站内消息 user_message」；
 * 短信 {@link SmsService#sendNotify} 为可插拔占位实现（未接入服务商时只落日志）。
 * 因此预约回复默认走站内消息，短信作为可选项，接入服务商后自动生效。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberCrmService {

    /** 生日提醒档位（提前天数） */
    private static final int[] BIRTHDAY_AHEAD_DAYS = {7, 3, 1, 0};
    /** 只有这一档发放关怀券，避免重复发券 */
    private static final int BIRTHDAY_COUPON_AHEAD = 7;

    private static final DateTimeFormatter MD_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final MemberAppointmentRepository appointmentRepository;
    private final MemberProfileRepository profileRepository;
    private final MemberVisitRepository visitRepository;
    private final IbigouOrderRepository ibigouOrderRepository;
    private final UserMessageRepository userMessageRepository;
    private final MerchantMessageRepository merchantMessageRepository;
    private final BirthdayRemindLogRepository birthdayLogRepository;
    private final CouponService couponService;
    private final AiAgentService aiAgentService;
    private final SmsService smsService;
    private final GlobalConfigService configService;

    // ==================== 1. 预约回复（含触达顾客） ====================

    /**
     * 商家回复预约。
     *
     * @param apptId   预约ID
     * @param action   confirm 确认 / reschedule 改期 / cancel 取消
     * @param content  回复内容（为空则由 AI 生成）
     * @param operator 操作人（商家账号；AI 自动回复时为 ai）
     * @param sendSms  是否同时发短信（需已接入服务商）
     */
    @Transactional
    public Map<String, Object> replyAppointment(String merchantNo, Long apptId, String action,
                                                String content, String operator, boolean sendSms) {
        MemberAppointment a = appointmentRepository.findById(apptId)
                .orElseThrow(() -> new RuntimeException("预约不存在"));
        if (!merchantNo.equals(a.getMerchantNo())) throw new RuntimeException("无权操作该预约");

        String text = (content == null || content.isBlank())
                ? aiAgentService.appointmentReply(merchantNo, a, action)
                : content.trim();

        LocalDateTime now = LocalDateTime.now();
        a.setReplyStatus(1);
        a.setReplyContent(text);
        a.setReplyTime(now);
        a.setReplyBy(operator == null || operator.isBlank() ? "merchant" : operator);

        // 触达顾客：站内消息（真实可达）+ 可选短信
        boolean smsOk = false;
        pushUserMessage(a.getUserPhone(), "appointment_reply",
                "预约回复：" + (a.getServiceItem() == null ? "服务预约" : a.getServiceItem()), text);
        if (sendSms) {
            try {
                smsOk = smsService.sendNotify(a.getUserPhone(), text);
            } catch (Exception e) {
                log.warn("[CRM] 预约回复短信发送失败 apptId={} err={}", apptId, e.getMessage());
            }
        }
        a.setReplySent(1);
        a.setReplyChannel(smsOk ? "both" : "user_message");
        appointmentRepository.save(a);

        Map<String, Object> r = new HashMap<>();
        r.put("id", a.getId());
        r.put("replyStatus", a.getReplyStatus());
        r.put("replyContent", a.getReplyContent());
        r.put("replyTime", a.getReplyTime());
        r.put("replyBy", a.getReplyBy());
        r.put("channel", a.getReplyChannel());
        r.put("smsSent", smsOk);
        return r;
    }

    /** 顾客提交/更新留言 */
    @Transactional
    public void submitCustomerMsg(String merchantNo, String userPhone, Long apptId, String msg) {
        MemberAppointment a = appointmentRepository.findById(apptId)
                .orElseThrow(() -> new RuntimeException("预约不存在"));
        if (!merchantNo.equals(a.getMerchantNo()) || !userPhone.equals(a.getUserPhone())) {
            throw new RuntimeException("无权操作该预约");
        }
        a.setCustomerMsg(msg);
        a.setReplyStatus(0);
        appointmentRepository.save(a);
    }

    // ==================== 2. 生日临近查询 ====================

    /**
     * 查询生日在 N 天内到期的会员（含倒计时与消费力）。
     */
    public List<Map<String, Object>> upcomingBirthdays(String merchantNo, int withinDays) {
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (MemberProfile p : profileRepository.findByMerchantNo(merchantNo)) {
            Long days = daysUntilBirthday(p.getBirthday(), today);
            if (days == null || days > withinDays) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("userPhone", p.getUserPhone());
            m.put("name", p.getName());
            m.put("memberType", p.getMemberType());
            m.put("birthday", p.getBirthday());
            m.put("daysUntil", days);
            m.put("customerPref", p.getCustomerPref());
            m.put("totalAmount", totalAmountOf(merchantNo, p.getUserPhone()));
            list.add(m);
        }
        list.sort(Comparator.comparing(m -> ((Number) m.get("daysUntil")).longValue()));
        return list;
    }

    /**
     * 距离下次生日的天数；birthday 为空或格式非法返回 null。
     * birthday 存储为 MM-dd。
     */
    public static Long daysUntilBirthday(String birthday, LocalDate today) {
        if (birthday == null || birthday.isBlank()) return null;
        MonthDay md;
        try {
            md = MonthDay.parse(birthday.trim(), MD_FMT);
        } catch (Exception e) {
            return null;
        }
        LocalDate next = safeDate(today.getYear(), md);
        if (next.isBefore(today)) {
            next = safeDate(today.getYear() + 1, md);
        }
        return ChronoUnit.DAYS.between(today, next);
    }

    /** 处理 2-29 等非闰年非法日期 */
    private static LocalDate safeDate(int year, MonthDay md) {
        try {
            return LocalDate.of(year, md.getMonthValue(), md.getDayOfMonth());
        } catch (Exception e) {
            return LocalDate.of(year, md.getMonthValue(), 1).withDayOfMonth(
                    LocalDate.of(year, md.getMonthValue(), 1).lengthOfMonth());
        }
    }

    // ==================== 3. 生日提醒定时任务（幂等） ====================

    /**
     * 每日执行：扫生日临近会员 → 商家站内提醒（7/3/1/0 天）+ 关怀券（7 天档）+ 顾客祝福（7/0 天档）。
     * 按 商家+会员+年份+档位 幂等，重复执行不会重复发券/重复打扰。
     *
     * @return 本次处理的条数
     */
    public int runBirthdayRemind() {
        LocalDate today = LocalDate.now();
        int handled = 0;
        for (MemberProfile p : profileRepository.findAll()) {
            if (p.getMerchantNo() == null || p.getMerchantNo().isBlank()) continue;
            Long d = daysUntilBirthday(p.getBirthday(), today);
            if (d == null) continue;
            int days = d.intValue();
            boolean matched = false;
            for (int ahead : BIRTHDAY_AHEAD_DAYS) {
                if (ahead == days) { matched = true; break; }
            }
            if (!matched) continue;

            try {
                handled += remindOne(p, days, today);
            } catch (Exception e) {
                log.warn("[CRM] 生日提醒处理失败 merchant={} phone={} err={}",
                        p.getMerchantNo(), p.getUserPhone(), e.getMessage());
            }
        }
        if (handled > 0) log.info("[CRM] 生日提醒完成，处理 {} 条", handled);
        return handled;
    }

    private int remindOne(MemberProfile p, int daysAhead, LocalDate today) {
        int year = today.getYear();
        if (birthdayLogRepository.existsByMerchantNoAndUserPhoneAndRemindYearAndDaysAhead(
                p.getMerchantNo(), p.getUserPhone(), year, daysAhead)) {
            return 0; // 已提醒过，幂等跳过
        }

        Long couponId = null;
        String couponDesc = null;

        // 7 天档：发放生日关怀券（立减券，面额可配置）
        if (daysAhead == BIRTHDAY_COUPON_AHEAD) {
            BigDecimal value = birthdayCouponValue();
            try {
                UserCoupon c = couponService.grant(p.getUserPhone(), p.getMerchantNo(),
                        PrizeType.CUT.getCode(), value, 1, "BIRTHDAY-" + year);
                couponId = c.getCouponId();
                couponDesc = "立减 " + value.stripTrailingZeros().toPlainString() + " 元生日券";
            } catch (Exception e) {
                log.warn("[CRM] 生日券发放失败 phone={} err={}", p.getUserPhone(), e.getMessage());
            }
        }

        // 商家站内提醒（每个档位都提醒）
        String when = daysAhead == 0 ? "今天生日" : (daysAhead + " 天后生日");
        pushMerchantMessage(p.getMerchantNo(), "birthday",
                "会员生日提醒：" + (p.getName() == null ? p.getUserPhone() : p.getName()) + " " + when,
                "手机号 " + mask(p.getUserPhone()) + "，" + when
                        + (couponDesc == null ? "" : "；已自动发放" + couponDesc)
                        + "；喜好：" + (p.getCustomerPref() == null ? "未填写" : p.getCustomerPref()));

        // 顾客祝福（只在 7 天档发券时与 0 天档当天触达，避免频繁打扰）
        if (daysAhead == BIRTHDAY_COUPON_AHEAD || daysAhead == 0) {
            String copy = aiAgentService.birthdayCopy(p.getMerchantNo(), p, couponDesc);
            pushUserMessage(p.getUserPhone(), "birthday", "生日祝福", copy);
            try {
                smsService.sendNotify(p.getUserPhone(), copy);
            } catch (Exception ignored) {
                // 短信未接入时不影响主流程
            }
        }

        BirthdayRemindLog logRow = new BirthdayRemindLog();
        logRow.setMerchantNo(p.getMerchantNo());
        logRow.setUserPhone(p.getUserPhone());
        logRow.setRemindYear(year);
        logRow.setDaysAhead(daysAhead);
        logRow.setCouponId(couponId);
        logRow.setCreateTime(LocalDateTime.now());
        birthdayLogRepository.save(logRow);
        return 1;
    }

    private BigDecimal birthdayCouponValue() {
        try {
            return new BigDecimal(configService.get("birthday_coupon_value", "20"));
        } catch (Exception e) {
            return new BigDecimal("20");
        }
    }

    // ==================== 4. 流失预警 ====================

    /**
     * 流失会员清单：风险分由规则计算，挽留建议由 AI 生成（不可用时降级规则文案）。
     */
    public List<Map<String, Object>> churnList(String merchantNo) {
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (MemberProfile p : profileRepository.findByMerchantNo(merchantNo)) {
            LocalDateTime last = lastVisitTime(merchantNo, p.getUserPhone());
            long days = last == null ? 999 : ChronoUnit.DAYS.between(last.toLocalDate(), today);
            int times = visitRepository.findByMerchantNoAndUserPhoneOrderByCreateTimeDesc(merchantNo, p.getUserPhone()).size();
            BigDecimal amount = totalAmountOf(merchantNo, p.getUserPhone());

            Map<String, Object> advice = aiAgentService.churnAdvice(merchantNo, p, days, times, amount);
            Map<String, Object> m = new HashMap<>();
            m.put("userPhone", p.getUserPhone());
            m.put("name", p.getName());
            m.put("memberType", p.getMemberType());
            m.put("lastVisitDays", days);
            m.put("visitTimes", times);
            m.put("totalAmount", amount);
            m.put("score", advice.get("score"));
            m.put("level", advice.get("level"));
            m.put("advice", advice.get("advice"));
            list.add(m);
        }
        list.sort((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")));
        return list;
    }

    /** 单个会员：回访话术 + 消费统计（供详情页「AI 回访建议」用） */
    public Map<String, Object> visitAdvice(String merchantNo, String userPhone) {
        MemberProfile p = profileRepository.findByMerchantNoAndUserPhone(merchantNo, userPhone).orElse(null);
        LocalDateTime last = lastVisitTime(merchantNo, userPhone);
        long days = last == null ? 999 : ChronoUnit.DAYS.between(last.toLocalDate(), LocalDate.now());
        int times = visitRepository.findByMerchantNoAndUserPhoneOrderByCreateTimeDesc(merchantNo, userPhone).size();
        BigDecimal amount = totalAmountOf(merchantNo, userPhone);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAmount", amount);
        stats.put("visitTimes", times);
        stats.put("lastVisitDays", days);

        Map<String, Object> r = new HashMap<>();
        r.put("stats", stats);
        r.put("script", aiAgentService.visitScript(merchantNo, p, stats));
        r.put("aiStatus", aiAgentService.status());
        return r;
    }

    // ==================== 辅助 ====================

    private LocalDateTime lastVisitTime(String merchantNo, String userPhone) {
        List<MemberVisit> vs = visitRepository.findByMerchantNoAndUserPhoneOrderByCreateTimeDesc(merchantNo, userPhone);
        return vs.isEmpty() ? null : vs.get(0).getVisitTime();
    }

    private BigDecimal totalAmountOf(String merchantNo, String userPhone) {
        // 消费口径：与订单页一致，仅统计未退款（refundStatus=0）的商城订单
        BigDecimal sum = BigDecimal.ZERO;
        try {
            List<IbigouOrder> orders = ibigouOrderRepository == null ? List.of()
                    : ibigouOrderRepository.findByUserPhoneOrderByCreateTimeDesc(userPhone);
            for (IbigouOrder o : orders) {
                if (o.getRefundStatus() != null && o.getRefundStatus() != 0) continue;
                if (o.getPayAmount() != null) sum = sum.add(o.getPayAmount());
            }
        } catch (Exception e) {
            log.warn("[CRM] 消费统计失败 phone={} err={}", userPhone, e.getMessage());
        }
        return sum;
    }

    private void pushUserMessage(String phone, String type, String title, String content) {
        UserMessage m = new UserMessage();
        m.setUserPhone(phone);
        m.setMsgType(type);
        m.setTitle(title);
        m.setContent(content);
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        userMessageRepository.save(m);
    }

    private void pushMerchantMessage(String merchantNo, String type, String title, String content) {
        MerchantMessage m = new MerchantMessage();
        m.setMerchantNo(merchantNo);
        m.setMsgType(type);
        m.setTitle(title);
        m.setContent(content);
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        merchantMessageRepository.save(m);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
