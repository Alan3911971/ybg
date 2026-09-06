package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.MemberWallet;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.UserBalanceFlow;
import com.ibigou.blindbox.repository.MemberWalletRepository;
import com.ibigou.blindbox.repository.MerchantRepository;
import com.ibigou.blindbox.repository.UserBalanceFlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商家钱包服务：余额按商家隔离
 */
@Service
@RequiredArgsConstructor
public class MerchantWalletService {

    private final MemberWalletRepository walletRepository;
    private final MerchantRepository merchantRepository;
    private final UserBalanceFlowRepository flowRepository;

    // ==================== 查询接口 ====================

    /**
     * 查询会员所有商家钱包列表（余额展示）
     */
    public List<WalletItem> listWallets(String memberId) {
        List<MemberWallet> wallets = walletRepository.findAllByMemberIdOrderByStoreId(memberId);
        return wallets.stream().map(this::toWalletItem).toList();
    }

    /**
     * 查询会员在指定商家的钱包余额
     */
    public WalletItem getBalance(String memberId, String storeId) {
        MemberWallet wallet = walletRepository.findByMemberIdAndStoreId(memberId, storeId)
                .orElseThrow(() -> new BizException("钱包不存在"));
        return toWalletItem(wallet);
    }

    // ==================== 交易接口 ====================

    /**
     * 获取会员在指定商家的流水（基于 user_balance_flow，按 storeId 过滤）
     */
    public List<TransactionItem> getTransactions(String memberId, String storeId) {
        List<UserBalanceFlow> flows = flowRepository.findByUserPhoneAndMerchantNoOrderByFlowIdDesc(memberId, storeId);
        return flows.stream().map(f -> new TransactionItem(
                String.valueOf(f.getFlowId()),
                f.getFlowType(),
                f.getAmount(),
                f.getMerchantNo(),
                null,
                f.getBizNo(),
                f.getRemark(),
                f.getCreateTime() != null ? f.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null
        )).toList();
    }

    /**
     * 商家端扣减会员余额（verify_type: 1=线下自动 2=手工 3=宜必购）
     */
    @Transactional
    public TransactionResult pay(String memberId, String storeId, BigDecimal amount,
                                  int verifyType, String bizNo, String remark) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException("扣减金额必须大于 0");
        }
        MemberWallet wallet = walletRepository.findByMemberIdAndStoreId(memberId, storeId)
                .orElseThrow(() -> new BizException("会员在本商家无钱包"));
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BizException("余额不足");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setTotalConsumed(wallet.getTotalConsumed().add(amount));
        wallet.setUpdateTime(LocalDateTime.now());
        walletRepository.save(wallet);

        // 记录余额扣减流水
        UserBalanceFlow flow = new UserBalanceFlow();
        flow.setUserPhone(memberId);
        flow.setFlowType(2); // 扣减
        flow.setAmount(amount.negate());
        flow.setCanUseAfterDraw(1);
        flow.setSourcePoolType(0);
        flow.setVerifyType(verifyType);
        flow.setMerchantNo(storeId);
        flow.setBizNo(bizNo);
        flow.setRemark(remark != null ? remark : "钱包余额扣减");
        flow.setCreateTime(LocalDateTime.now());
        flowRepository.save(flow);

        return new TransactionResult(true, wallet.getBalance(), "扣减成功");
    }

    // ==================== 内部方法 ====================

    /** 发放余额给会员（供其他 service 调用） */
    @Transactional
    public void grant(String memberId, String storeId, BigDecimal amount, String remark) {
        if (amount == null || amount.signum() <= 0) return;
        Merchant merchant = merchantRepository.findByMerchantNo(storeId).orElse(null);
        String storeName = merchant != null ? merchant.getMerchantName() : storeId;

        MemberWallet wallet = walletRepository.findByMemberIdAndStoreId(memberId, storeId)
                .orElseGet(() -> {
                    MemberWallet w = new MemberWallet();
                    w.setMemberId(memberId);
                    w.setStoreId(storeId);
                    w.setStoreName(storeName);
                    w.setBalance(BigDecimal.ZERO);
                    w.setFrozenBalance(BigDecimal.ZERO);
                    w.setTotalGranted(BigDecimal.ZERO);
                    w.setTotalConsumed(BigDecimal.ZERO);
                    w.setCreateTime(LocalDateTime.now());
                    w.setUpdateTime(LocalDateTime.now());
                    return w;
                });

        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setTotalGranted(wallet.getTotalGranted().add(amount));
        wallet.setUpdateTime(LocalDateTime.now());
        walletRepository.save(wallet);
    }

    /** 发放余额并返回最新钱包快照 */
    @Transactional
    public WalletItem grantAndReturn(String memberId, String storeId, BigDecimal amount, String remark) {
        grant(memberId, storeId, amount, remark);
        return toWalletItem(walletRepository.findByMemberIdAndStoreId(memberId, storeId)
                .orElseThrow(() -> new BizException("钱包不存在")));
    }

    private WalletItem toWalletItem(MemberWallet w) {
        return new WalletItem(
                w.getMemberId(),
                w.getStoreId(),
                w.getStoreName(),
                w.getBalance(),
                w.getFrozenBalance(),
                w.getTotalGranted(),
                w.getTotalConsumed()
        );
    }

    // ==================== DTO ====================

    public record WalletItem(
            String memberId,
            String storeId,
            String storeName,
            BigDecimal balance,
            BigDecimal frozenBalance,
            BigDecimal totalGranted,
            BigDecimal totalConsumed
    ) {}

    public record TransactionItem(
            String txId,
            int txType,
            BigDecimal amount,
            String storeId,
            String storeName,
            String bizNo,
            String remark,
            String createTime
    ) {}

    public record TransactionResult(
            boolean success,
            BigDecimal remainingBalance,
            String message
    ) {}
}
