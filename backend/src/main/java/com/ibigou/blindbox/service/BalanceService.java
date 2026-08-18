package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.UserAccount;
import com.ibigou.blindbox.entity.UserBalanceFlow;
import com.ibigou.blindbox.enums.FlowType;
import com.ibigou.blindbox.repository.UserAccountRepository;
import com.ibigou.blindbox.repository.UserBalanceFlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 余额资产服务。
 * <p>约定：user_account.total_balance 恒等于可用余额
 * （can_use_after_draw=1 的净额）；刚抽出的发放流水 can_use_after_draw=0，
 * 流程闭环时流水置 1 并累加 total_balance。</p>
 * <p>抵扣上限 = 优惠后实付金额 × balance_deduct_rate（线下、宜必购共用）。</p>
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final UserAccountRepository accountRepository;
    private final UserBalanceFlowRepository flowRepository;

    /** 抽奖发放余额：入流水 can_use_after_draw=0，不动 total_balance */
    @Transactional
    public UserBalanceFlow grant(String userPhone, String merchantNo, BigDecimal amount, int sourcePoolType,
                                 String drawBatchNo, String remark) {
        UserBalanceFlow f = new UserBalanceFlow();
        f.setUserPhone(userPhone);
        f.setMerchantNo(merchantNo);
        f.setFlowType(FlowType.GRANT.getCode());
        f.setAmount(amount);
        f.setCanUseAfterDraw(0);
        f.setSourcePoolType(sourcePoolType);
        f.setVerifyType(0);
        f.setDrawBatchNo(drawBatchNo);
        f.setRemark(remark);
        f.setCreateTime(LocalDateTime.now());
        return flowRepository.save(f);
    }

    /** 发放余额且立即可用（跨店返还等，非抽奖资产） */
    @Transactional
    public UserBalanceFlow grantAvailable(String userPhone, String merchantNo, BigDecimal amount,
                                          String remark, String bizNo) {
        UserAccount account = accountRepository.findById(userPhone).orElse(null);
        if (account == null) {
            account = new UserAccount();
            account.setUserPhone(userPhone);
            account.setTotalBalance(BigDecimal.ZERO);
            account.setUpdateTime(LocalDateTime.now());
        }
        account.setTotalBalance(account.getTotalBalance().add(amount));
        account.setUpdateTime(LocalDateTime.now());
        accountRepository.save(account);

        UserBalanceFlow f = new UserBalanceFlow();
        f.setUserPhone(userPhone);
        f.setMerchantNo(merchantNo);
        f.setFlowType(FlowType.GRANT.getCode());
        f.setAmount(amount);
        f.setCanUseAfterDraw(1);
        f.setSourcePoolType(0);
        f.setVerifyType(0);
        f.setBizNo(bizNo);
        f.setRemark(remark);
        f.setCreateTime(LocalDateTime.now());
        return flowRepository.save(f);
    }

    /** 可用余额 */
    public BigDecimal availableBalance(String userPhone) {
        return accountRepository.findById(userPhone)
                .map(UserAccount::getTotalBalance)
                .orElse(BigDecimal.ZERO);
    }

    /** 余额最大可抵扣上限 = 优惠后实付 × rate */
    public BigDecimal deductLimit(BigDecimal afterCouponAmount, int balanceDeductRate) {
        if (afterCouponAmount == null || afterCouponAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return afterCouponAmount.multiply(BigDecimal.valueOf(balanceDeductRate))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
    }

    /** 扣减：校验上限与可用余额；verify_type 1线下自动 2手工 3宜必购 */
    @Transactional
    public UserBalanceFlow deduct(String userPhone, String merchantNo, BigDecimal deductAmount, BigDecimal limit,
                                  int verifyType, String bizNo) {
        if (deductAmount == null || deductAmount.signum() <= 0) {
            throw new BizException("余额抵扣金额必须大于 0");
        }
        if (deductAmount.compareTo(limit) > 0) {
            throw new BizException("余额抵扣金额超过最大抵扣上限");
        }
        UserAccount account = accountRepository.findByIdForUpdate(userPhone)
                .orElseThrow(() -> new BizException("用户余额账户不存在"));
        if (deductAmount.compareTo(account.getTotalBalance()) > 0) {
            throw new BizException("余额不足");
        }
        account.setTotalBalance(account.getTotalBalance().subtract(deductAmount));
        account.setUpdateTime(LocalDateTime.now());
        accountRepository.save(account);

        UserBalanceFlow f = new UserBalanceFlow();
        f.setUserPhone(userPhone);
        f.setMerchantNo(merchantNo);
        f.setFlowType(FlowType.DEDUCT.getCode());
        f.setAmount(deductAmount.negate());
        f.setCanUseAfterDraw(1);
        f.setSourcePoolType(0); // 消耗不区分来源池，报表按 verify_type 区分
        f.setVerifyType(verifyType);
        f.setBizNo(bizNo);
        f.setRemark("余额扣减");
        f.setCreateTime(LocalDateTime.now());
        return flowRepository.save(f);
    }

    /** 退款退回余额：全额/部分均按金额退回，flow_type=3 */
    @Transactional
    public UserBalanceFlow refund(String userPhone, String merchantNo, BigDecimal refundAmount, String bizNo, String remark) {
        if (refundAmount == null || refundAmount.signum() <= 0) {
            throw new BizException("退款金额必须大于 0");
        }
                UserAccount account = accountRepository.findByIdForUpdate(userPhone)
                .orElseThrow(() -> new BizException("用户余额账户不存在"));
        account.setTotalBalance(account.getTotalBalance().add(refundAmount));
        account.setUpdateTime(LocalDateTime.now());
        accountRepository.save(account);

        UserBalanceFlow f = new UserBalanceFlow();
        f.setUserPhone(userPhone);
        f.setMerchantNo(merchantNo);
        f.setFlowType(FlowType.REFUND.getCode());
        f.setAmount(refundAmount);
        f.setCanUseAfterDraw(1);
        f.setSourcePoolType(0);
        f.setVerifyType(0);
        f.setBizNo(bizNo);
        f.setRemark(remark);
        f.setCreateTime(LocalDateTime.now());
        return flowRepository.save(f);
    }

    /** 跨店返还扣回（退款时）：允许台账负余额（定稿硬性约束第 10 条） */
    @Transactional
    public UserBalanceFlow clawbackReturn(String userPhone, String merchantNo, BigDecimal amount, String bizNo) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        UserAccount account = accountRepository.findByIdForUpdate(userPhone)
                .orElseThrow(() -> new BizException("用户余额账户不存在"));
        account.setTotalBalance(account.getTotalBalance().subtract(amount)); // 允许负
        account.setUpdateTime(LocalDateTime.now());
        accountRepository.save(account);
        UserBalanceFlow f = new UserBalanceFlow();
        f.setUserPhone(userPhone);
        f.setMerchantNo(merchantNo);
        f.setFlowType(FlowType.DEDUCT.getCode());
        f.setAmount(amount.negate());
        f.setCanUseAfterDraw(1);
        f.setSourcePoolType(0);
        f.setVerifyType(0);
        f.setBizNo(bizNo);
        f.setRemark("跨店返还扣回(退款)");
        f.setCreateTime(LocalDateTime.now());
        return flowRepository.save(f);
    }

    /** 流程闭环：批次内 can_use_after_draw=0 的发放流水置 1 并累加 total_balance */
    @Transactional
    public void activateDrawBatch(String drawBatchNo) {
        if (drawBatchNo == null || drawBatchNo.isBlank()) {
            return;
        }
        // 原子激活：并发第二次 affected=0 直接返回，防余额双加
        int affected = flowRepository.activateByBatch(drawBatchNo);
        if (affected <= 0) {
            return;
        }
        BigDecimal add = flowRepository.sumGrantByBatch(drawBatchNo);
        if (add != null && add.signum() > 0) {
            String userPhone = flowRepository.findFirstByDrawBatchNoOrderByFlowIdAsc(drawBatchNo)
                    .map(UserBalanceFlow::getUserPhone).orElse(null);
            if (userPhone != null) {
                UserAccount account = accountRepository.findByIdForUpdate(userPhone).orElse(null);
                if (account == null) {
                    account = new UserAccount();
                    account.setUserPhone(userPhone);
                    account.setTotalBalance(BigDecimal.ZERO);
                    account.setUpdateTime(LocalDateTime.now());
                }
                account.setTotalBalance(account.getTotalBalance().add(add));
                account.setUpdateTime(LocalDateTime.now());
                accountRepository.save(account);
            }
        }
    }
}
