package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.AuditLog;
import com.ibigou.blindbox.entity.MemberOrder;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.SysGlobalConfig;
import com.ibigou.blindbox.repository.AuditLogRepository;
import com.ibigou.blindbox.repository.MemberOrderRepository;
import com.ibigou.blindbox.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * V1.5 商家会员续费体系（定稿第六章）。
 * <p>规则：免费试用天数/月单价/赠送活动全部平台后台配置（代码不写死）；
 * 到期后仅按年续费，年费 = 月单价×12；赠送仅对新续费订单生效；
 * 有效期叠加：免费期内→从免费期结束起算；未到期→向后叠加；已过期→从当天起算。</p>
 * <p>写锁定：会员过期后开盲盒/下单/兜底/退款等写操作全部拦截（查询只读不受限）。</p>
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    public static final String MEMBER_EXPIRE_MSG = "会员已到期，请完成年费续费后再使用该功能";

    private final MerchantRepository merchantRepository;
    private final MemberOrderRepository orderRepository;
    private final AuditLogRepository auditLogRepository;
    private final GlobalConfigService configService;
    private final AuditLogService auditLogService;

    // ---------------- 配置读取（平台后台可改，代码不写死） ----------------

    public int freeTrialDays() {
        return parseInt(configService.get("free_trial_days"), 30);
    }

    public BigDecimal monthlyPrice() {
        return new BigDecimal(configService.get("monthly_price", "150"));
    }

    public boolean giftSwitchOn() {
        return parseInt(configService.get("renew_gift_switch"), 0) == 1;
    }

    public int giftMonths() {
        return giftSwitchOn() ? parseInt(configService.get("renew_gift_months"), 0) : 0;
    }

    /** 年费 = 月单价 × 12 */
    public BigDecimal annualPrice() {
        return monthlyPrice().multiply(BigDecimal.valueOf(12));
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    // ---------------- 会员状态 ----------------

    /** 会员状态：0 免费试用中 / 1 已付费-有效 / 2 已过期 */
    public MemberStatus status(String merchantNo) {
        Merchant m = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));
        LocalDateTime expire = m.getMemberExpireTime();
        if (expire == null) {
            // 旧数据未设有效期：视为永久有效
            return new MemberStatus(1, null, 0L, true, expire);
        }
        boolean paid = orderRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo).stream()
                .anyMatch(o -> o.getStatus() == 1);
        long remainDays = java.time.Duration.between(LocalDateTime.now(), expire).toDays();
        boolean expired = expire.isBefore(LocalDateTime.now());
        int state = expired ? 2 : (paid ? 1 : 0);
        return new MemberStatus(state, expire, remainDays, !expired, expire);
    }

    /** 写操作校验：会员过期抛业务异常（查询类接口不调用） */
    public void requireWriteAllowed(String merchantNo) {
        if (merchantNo == null || merchantNo.isBlank() || "PLATFORM".equals(merchantNo)) {
            return; // 平台/无门店上下文不校验
        }
        Merchant m = merchantRepository.findById(merchantNo).orElse(null);
        if (m == null || m.getMemberExpireTime() == null) {
            return; // 不存在或永久有效
        }
        if (m.getMemberExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(MEMBER_EXPIRE_MSG);
        }
    }

    // ---------------- 续费订单 ----------------

    /** 生成续费订单（年费 = 月单价×12；赠送月数按活动开关） */
    @Transactional
    public MemberOrder createRenewOrder(String merchantNo) {
        merchantRepository.findById(merchantNo).orElseThrow(() -> new BizException("商家不存在"));
        int gift = giftMonths();
        MemberOrder order = new MemberOrder();
        order.setOrderNo("REN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        order.setMerchantNo(merchantNo);
        order.setAmount(annualPrice());
        order.setBuyMonths(12);
        order.setGiftMonths(gift);
        order.setTotalMonths(12 + gift);
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return orderRepository.save(order);
    }

    /**
     * 确认支付成功（真实微信支付回调 P1 对接；本方法为业务闭环入口）。
     * 有效期叠加：免费期内→从免费期结束起算；未到期→向后叠加；已过期→从当天起算。
     */
    @Transactional
    public MemberOrder confirmRenewPaid(String orderNo, String operator) {
        MemberOrder order = orderRepository.findById(orderNo)
                .orElseThrow(() -> new BizException("续费订单不存在"));
        if (order.getStatus() == 1) {
            throw new BizException("订单已支付，不能重复确认");
        }
        Merchant m = merchantRepository.findById(order.getMerchantNo())
                .orElseThrow(() -> new BizException("商家不存在"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = m.getMemberExpireTime();
        LocalDateTime validFrom;
        if (base == null || base.isBefore(now)) {
            // 已过期 / 无有效期：从当天起算
            validFrom = now;
        } else {
            // 免费期内或未到期：从原有效期继续向后叠加（免费期内续费 = 免费期结束才起算）
            validFrom = base;
        }
        LocalDateTime validTo = validFrom.plusMonths(order.getTotalMonths());
        m.setMemberExpireTime(validTo);
        m.setUpdateTime(now);
        merchantRepository.save(m);

        order.setStatus(1);
        order.setValidFrom(validFrom);
        order.setValidTo(validTo);
        order.setPayTime(now);
        order.setUpdateTime(now);
        MemberOrder saved = orderRepository.save(order);

        audit(operator, order.getMerchantNo(), "renew_paid", null, order.getAmount(),
                "续费确认 order=" + orderNo + " 增加" + order.getTotalMonths() + "个月 至 " + fmt(validTo));
        return saved;
    }

    /** 续费记录 */
    public List<MemberOrder> orders(String merchantNo) {
        return orderRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo);
    }

    /** 全平台续费订单 */
    public List<MemberOrder> allOrders() {
        return orderRepository.findAll();
    }

    /** 获取订单并校验归属商家 */
    public MemberOrder getOrder(String orderNo, String merchantNo) {
        MemberOrder order = orderRepository.findById(orderNo)
                .orElseThrow(() -> new BizException("续费订单不存在"));
        if (!order.getMerchantNo().equals(merchantNo)) {
            throw new BizException("无权操作该订单");
        }
        return order;
    }

    // ---------------- 平台人工调整（审计留存） ----------------

    /** 平台手动调整商家有效期（增加赠送月份），留审计日志 */
    @Transactional
    public void adminAdjustExpire(String merchantNo, int months, String operator) {
        if (months <= 0) {
            throw new BizException("调整月数必须大于 0");
        }
        Merchant m = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = m.getMemberExpireTime();
        LocalDateTime newExpire = (base == null || base.isBefore(now) ? now : base).plusMonths(months);
        m.setMemberExpireTime(newExpire);
        m.setUpdateTime(now);
        merchantRepository.save(m);
        auditLogService.record(operator, merchantNo, "adjust_expire", null,
                java.math.BigDecimal.valueOf(months), "平台人工调整有效期 +" + months + " 个月，至 " + fmt(newExpire),
                AuditLogService.RT_EXPIRE_DECREASE, merchantNo);
    }

    // ---------------- 审计 ----------------

    @Transactional
    public void audit(String operator, String merchantNo, String action, String userPhone,
                      BigDecimal amount, String detail) {
        AuditLog log = new AuditLog();
        log.setMerchantNo(merchantNo);
        log.setOperator(operator);
        log.setAction(action);
        log.setUserPhone(userPhone);
        log.setAmount(amount);
        log.setDetail(detail);
        log.setRevokeType("NONE");
        log.setRevoked(0);
        log.setCreateTime(LocalDateTime.now());
        auditLogRepository.save(log);
    }

    public List<AuditLog> allAudits() {
        return auditLogRepository.findAll();
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 会员状态 DTO */
    public record MemberStatus(int state, LocalDateTime expireTime, long remainDays,
                               boolean writeAllowed, LocalDateTime memberExpireTime) {
    }
}
