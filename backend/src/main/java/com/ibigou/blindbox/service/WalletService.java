package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.UserBalanceFlow;
import com.ibigou.blindbox.entity.UserCoupon;
import com.ibigou.blindbox.enums.CouponStatus;
import com.ibigou.blindbox.repository.UserBalanceFlowRepository;
import com.ibigou.blindbox.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户钱包查询（V1.4 5.1.4）。
 * 券分组：可用 / 暂不可用 / 外来公共券 / 已核销 / 已过期；
 * 余额：总可用余额 + 流水；可用性全部后端判定，前端只展示。
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserCouponRepository couponRepository;
    private final UserBalanceFlowRepository flowRepository;
    private final BalanceService balanceService;

    public Wallet wallet(String userPhone) {
        List<UserCoupon> all = couponRepository.findByUserPhoneOrderByCouponIdDesc(userPhone);
        List<UserCoupon> available = new java.util.ArrayList<>();
        List<UserCoupon> pending = new java.util.ArrayList<>();
        List<UserCoupon> used = new java.util.ArrayList<>();
        List<UserCoupon> expiredOrVoid = new java.util.ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (UserCoupon c : all) {
            if (c.getStatus() == CouponStatus.VERIFIED.getCode()) {
                used.add(c);
            } else if (c.getStatus() == CouponStatus.EXPIRED.getCode()
                    || c.getStatus() == CouponStatus.VOIDED.getCode()
                    || (c.getStatus() == CouponStatus.UNUSED.getCode() && c.getValidEnd() != null && c.getValidEnd().isBefore(now))) {
                expiredOrVoid.add(c);
            } else if (c.getCanUseAfterDraw() != 1) {
                pending.add(c);
            } else {
                // 可用券（含外来公共券：前端按 sourceMerchantNo 展示来源商家，
                // 提示"前往 XX 商家线下门店或宜必购渠道使用"，见 V1.4 5.1.4）
                available.add(c);
            }
        }
        return new Wallet(balanceService.availableBalance(userPhone), available, pending,
                used, expiredOrVoid,
                flowRepository.findByUserPhoneOrderByFlowIdDesc(userPhone));
    }

    public record Wallet(BigDecimal balance,
                         List<UserCoupon> available,
                         List<UserCoupon> pending,
                         List<UserCoupon> used,
                         List<UserCoupon> expiredOrVoid,
                         List<UserBalanceFlow> flows) {
    }
}
