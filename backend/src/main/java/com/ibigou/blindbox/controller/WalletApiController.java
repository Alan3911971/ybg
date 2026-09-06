package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.service.MerchantWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * 钱包按商家隔离 API
 * 测试路由：/api/wallet/*
 * 认证：商家端 token（X-Merchant-Token header）
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletApiController {

    private final MerchantWalletService walletService;

    /**
     * 查询会员在所有商家的钱包列表
     * GET /api/wallet/list?memberId=13800138000
     */
    @GetMapping("/list")
    public Result<List<MerchantWalletService.WalletItem>> list(
            @RequestParam String memberId) {
        return Result.ok(walletService.listWallets(memberId));
    }

    /**
     * 查询会员在指定商家的钱包余额
     * GET /api/wallet/balance?memberId=13800138000&storeId=M001
     */
    @GetMapping("/balance")
    public Result<MerchantWalletService.WalletItem> balance(
            @RequestParam String memberId,
            @RequestParam String storeId) {
        return Result.ok(walletService.getBalance(memberId, storeId));
    }

    /**
     * 查询会员在指定商家的交易流水
     * GET /api/wallet/transactions?memberId=13800138000&storeId=M001
     */
    @GetMapping("/transactions")
    public Result<List<MerchantWalletService.TransactionItem>> transactions(
            @RequestParam String memberId,
            @RequestParam String storeId) {
        return Result.ok(walletService.getTransactions(memberId, storeId));
    }

    /**
     * 商家端扣减会员余额（消费/核销）
     * POST /api/wallet/pay
     * Body: memberId, storeId, amount
     */
    @PostMapping("/pay")
    public Result<MerchantWalletService.TransactionResult> pay(
            @RequestParam String memberId,
            @RequestParam String storeId,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "2") int verifyType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) String remark) {
        return Result.ok(walletService.pay(memberId, storeId, amount, verifyType, bizNo, remark));
    }

    /**
     * 平台/测试：给会员在指定商家发放余额（充值/奖励）
     * POST /api/wallet/grant
     * Body: memberId, storeId, amount, remark(可选)
     * Header: X-Admin-Token（平台管理员 token）
     */
    @PostMapping("/grant")
    public Result<MerchantWalletService.WalletItem> grant(
            @RequestParam String memberId,
            @RequestParam String storeId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String remark) {
        return Result.ok(walletService.grantAndReturn(memberId, storeId, amount, remark));
    }
}
