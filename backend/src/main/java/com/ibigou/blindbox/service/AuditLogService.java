package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.AuditLog;
import com.ibigou.blindbox.entity.OfflineOrder;
import com.ibigou.blindbox.entity.UserCoupon;
import com.ibigou.blindbox.enums.CouponStatus;
import com.ibigou.blindbox.repository.AuditLogRepository;
import com.ibigou.blindbox.repository.MerchantRepository;
import com.ibigou.blindbox.repository.OfflineOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * V1.5 人工操作审计与撤销（定稿第七章/平台后台清单第 4 条）。
 * <p>记录商家每笔人工操作（手工核销券/兜底完成/退款）+ 平台调整；
 * 平台可撤销违规操作并恢复余额、券状态（幂等：每条只能撤销一次）。</p>
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String RT_COUPON_RESTORE = "COUPON_RESTORE";
    public static final String RT_BALANCE_RETURN = "BALANCE_RETURN";
    public static final String RT_REFUND_REVERSE = "REFUND_REVERSE";
    public static final String RT_EXPIRE_DECREASE = "EXPIRE_DECREASE";

    private final AuditLogRepository auditLogRepository;
    private final CouponService couponService;
    private final BalanceService balanceService;
    private final OrderCalcService orderCalcService;
    private final OfflineOrderRepository offlineOrderRepository;
    private final MerchantRepository merchantRepository;

    /** 记录审计（撤销类型与关联ID） */
    @Transactional
    public AuditLog record(String operator, String merchantNo, String action, String userPhone,
                           BigDecimal amount, String detail, String revokeType, String refId) {
        AuditLog log = new AuditLog();
        log.setMerchantNo(merchantNo);
        log.setOperator(operator);
        log.setAction(action);
        log.setUserPhone(userPhone);
        log.setAmount(amount);
        log.setDetail(detail);
        log.setRevokeType(revokeType == null ? "NONE" : revokeType);
        log.setRefId(refId);
        log.setRevoked(0);
        log.setCreateTime(LocalDateTime.now());
        return auditLogRepository.save(log);
    }

    /** 撤销审计记录（恢复余额/券状态；幂等） */
    @Transactional
    public AuditLog revoke(Long logId, String adminOperator) {
        AuditLog log = auditLogRepository.findById(logId)
                .orElseThrow(() -> new BizException("审计记录不存在"));
        if (log.getRevoked() == 1) {
            throw new BizException("该记录已撤销，不能重复撤销");
        }
        String rt = log.getRevokeType() == null ? "NONE" : log.getRevokeType();
        switch (rt) {
            case RT_COUPON_RESTORE -> revokeCouponRestore(log);
            case RT_BALANCE_RETURN -> revokeBalanceReturn(log);
            case RT_REFUND_REVERSE -> revokeRefundReverse(log);
            case RT_EXPIRE_DECREASE -> revokeExpireDecrease(log);
            default -> throw new BizException("该记录不支持撤销");
        }
        log.setRevoked(1);
        log.setRevokeTime(LocalDateTime.now());
        log.setDetail(log.getDetail() + " | 已撤销 by " + adminOperator + " " + LocalDateTime.now());
        return auditLogRepository.save(log);
    }

    /** 撤销手工核销券：恢复券为未核销 */
    private void revokeCouponRestore(AuditLog log) {
        if (log.getRefId() == null) {
            throw new BizException("缺少券关联ID");
        }
        UserCoupon coupon = couponService.get(Long.parseLong(log.getRefId()));
        coupon.setStatus(CouponStatus.UNUSED.getCode());
        coupon.setVerifyType(0);
        coupon.setBizNo(null);
        couponService.restore(coupon.getCouponId());
    }

    /** 撤销兜底完成：退回抵扣余额 + 返还单日额度 */
    private void revokeBalanceReturn(AuditLog log) {
        if (log.getUserPhone() == null || log.getMerchantNo() == null || log.getAmount() == null) {
            throw new BizException("缺少退款所需信息");
        }
        balanceService.refund(log.getUserPhone(), log.getMerchantNo(), log.getAmount(),
                "AUDIT-" + log.getLogId(), "审计撤销(兜底抵扣退回)");
        orderCalcService.refundQuota(log.getUserPhone(), log.getMerchantNo(), log.getAmount());
    }

    /** 撤销退款：反向执行（券重新核销、余额重新扣回、额度加回、返还重新扣回） */
    private void revokeRefundReverse(AuditLog log) {
        if (log.getRefId() == null) {
            throw new BizException("缺少订单关联ID");
        }
        OfflineOrder order = offlineOrderRepository.findById(log.getRefId())
                .orElseThrow(() -> new BizException("订单不存在"));
        // 券重新核销
        if (order.getCouponId() != null) {
            UserCoupon coupon = couponService.get(order.getCouponId());
            coupon.setStatus(CouponStatus.VERIFIED.getCode());
            coupon.setVerifyType(1);
            coupon.setBizNo(order.getOfflineOrderNo());
        }
        // 余额重新扣回（抵扣部分）+ 额度加回
        if (order.getDeductBalance() != null && order.getDeductBalance().signum() > 0) {
            balanceService.deduct(order.getUserPhone(), order.getMerchantNo(), order.getDeductBalance(),
                    order.getDeductBalance(), 1, order.getOfflineOrderNo());
            orderCalcService.addQuota(order.getUserPhone(), order.getMerchantNo(), order.getDeductBalance());
        }
        // 返还余额重新扣回
        if (order.getReturnBalance() != null && order.getReturnBalance().signum() > 0) {
            balanceService.clawbackReturn(order.getUserPhone(), order.getMerchantNo(),
                    order.getReturnBalance(), "AUDIT-" + log.getLogId());
        }
        // 订单状态回到已退款前（已完成）
        order.setRefundStatus(0);
        order.setRefundAmount(BigDecimal.ZERO);
        order.setRefundTime(null);
        order.setUpdateTime(LocalDateTime.now());
        offlineOrderRepository.save(order);
    }

    /** 撤销有效期调整：有效期减回 */
    private void revokeExpireDecrease(AuditLog log) {
        if (log.getRefId() == null) {
            throw new BizException("缺少商家号");
        }
        var merchant = merchantRepository.findById(log.getRefId())
                .orElseThrow(() -> new BizException("商家不存在"));
        int months = log.getAmount() == null ? 0 : log.getAmount().intValue();
        if (merchant.getMemberExpireTime() != null) {
            merchant.setMemberExpireTime(merchant.getMemberExpireTime().minusMonths(months));
            merchant.setUpdateTime(LocalDateTime.now());
            merchantRepository.save(merchant);
        }
    }

    public List<AuditLog> all() {
        return auditLogRepository.findAllByOrderByLogIdDesc();
    }
}
