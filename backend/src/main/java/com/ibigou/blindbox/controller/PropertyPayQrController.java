package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.PropertyBill;
import com.ibigou.blindbox.entity.PropertyCompany;
import com.ibigou.blindbox.entity.PropertyOwner;
import com.ibigou.blindbox.entity.PropertyPrizePool;
import com.ibigou.blindbox.entity.PropertyWallet;
import com.ibigou.blindbox.repository.PropertyBillRepository;
import com.ibigou.blindbox.repository.PropertyCompanyRepository;
import com.ibigou.blindbox.repository.PropertyOwnerRepository;
import com.ibigou.blindbox.repository.PropertyPaymentRepository;
import com.ibigou.blindbox.repository.PropertyPrizePoolRepository;
import com.ibigou.blindbox.repository.PropertyWalletRepository;
import com.ibigou.blindbox.service.PropertyBillPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 物业缴费二维码（公开扫码端点）：物业后台生成缴费二维码 → 业主扫码 → 手机号验证 → 微信/支付宝支付（PB- 回调）。
 * 与商家收款码模式一致：免登录、手机号定位业主、动态金额来自账单。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/pay-qr")
@RequiredArgsConstructor
public class PropertyPayQrController {

    private final PropertyBillRepository billRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyCompanyRepository companyRepository;
    private final PropertyPaymentRepository paymentRepository;
    private final PropertyBillPaymentService propertyBillPaymentService;
    private final PropertyPrizePoolRepository prizePoolRepository;
    private final PropertyWalletRepository walletRepository;

    /** 扫码落地页：账单概要（免登录） */
    @GetMapping("/bill")
    public Result<Map<String, Object>> billInfo(@RequestParam Long billId) {
        PropertyBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("账单不存在"));
        BigDecimal amount = nz(bill.getAmount());
        BigDecimal deducted = nz(bill.getDeducted());
        BigDecimal paid = nz(bill.getPaid());
        BigDecimal remaining = amount.subtract(deducted).subtract(paid);
        Map<String, Object> m = new HashMap<>();
        m.put("billId", bill.getBillId());
        m.put("billNo", bill.getBillNo());
        m.put("billPeriod", bill.getBillPeriod());
        m.put("billType", bill.getBillType());
        m.put("amount", amount);
        m.put("remaining", remaining.max(BigDecimal.ZERO));
        m.put("paid", paid);
        m.put("status", bill.getStatus());
        PropertyCompany company = bill.getCompanyId() == null ? null
                : companyRepository.findById(bill.getCompanyId()).orElse(null);
        m.put("companyName", company == null ? "物业公司" : company.getCompanyName());
        return Result.ok(m);
    }

    /** 手机号验证 + 创建支付单（微信/支付宝），返回 qrCode 供页面轮询展示；钱包余额自动抵扣 */
    @PostMapping("/pay")
    public Result<Map<String, Object>> pay(@RequestParam Long billId,
                                           @RequestParam String phone,
                                           @RequestParam String channel) {
        PropertyOwner owner = ownerRepository.findByOwnerPhone(phone)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("该手机号未绑定业主，请确认是否已登记"));
        PropertyBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("账单不存在"));
        BigDecimal remaining = nz(bill.getAmount()).subtract(nz(bill.getDeducted())).subtract(nz(bill.getPaid()));
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.ibigou.blindbox.common.BizException("账单已缴清");
        }
        BigDecimal walletUsed = BigDecimal.ZERO;
        BigDecimal payAmount = remaining;
        PropertyWallet w = walletRepository.findByOwnerIdAndCompanyId(owner.getOwnerId(), bill.getCompanyId()).orElse(null);
        if (w != null && w.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            if (w.getBalance().compareTo(remaining) >= 0) {
                // 余额足够：全额抵扣，账单直接结清
                walletUsed = remaining;
                w.setBalance(w.getBalance().subtract(remaining));
                w.setUpdateTime(java.time.LocalDateTime.now());
                walletRepository.save(w);
                bill.setDeducted(nz(bill.getDeducted()).add(remaining));
                bill.setStatus(1);
                billRepository.save(bill);
                Map<String, Object> r = new HashMap<>();
                r.put("walletUsed", walletUsed);
                r.put("walletBalance", w.getBalance());
                r.put("fullyPaid", true);
                r.put("amount", BigDecimal.ZERO);
                r.put("paymentNo", "");
                r.put("qrCode", "");
                r.put("ownerName", owner.getOwnerName());
                r.put("ownerPhone", phone);
                return Result.ok(r);
            } else {
                // 余额不足：部分抵扣
                walletUsed = w.getBalance();
                w.setBalance(BigDecimal.ZERO);
                w.setUpdateTime(java.time.LocalDateTime.now());
                walletRepository.save(w);
                bill.setDeducted(nz(bill.getDeducted()).add(walletUsed));
                billRepository.save(bill);
                payAmount = remaining.subtract(walletUsed);
            }
        }
        Map<String, Object> r = propertyBillPaymentService.createPayment(owner.getOwnerId(), billId, channel);
        r.put("walletUsed", walletUsed);
        r.put("walletBalance", w == null ? BigDecimal.ZERO : w.getBalance());
        r.put("fullyPaid", false);
        r.put("ownerName", owner.getOwnerName());
        r.put("ownerPhone", phone);
        return Result.ok(r);
    }

    /** 支付状态轮询（paymentNo 为随机长串，可作查询凭据） */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(@RequestParam String paymentNo) {
        com.ibigou.blindbox.entity.PropertyPayment payment = paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("支付单不存在"));
        Map<String, Object> m = new HashMap<>();
        m.put("paymentNo", paymentNo);
        m.put("status", payment.getStatus());
        m.put("paid", payment.getStatus() != null && payment.getStatus() == 1);
        m.put("billId", payment.getBillId());
        return Result.ok(m);
    }

    /** 公司级扫码：手机号查该公司待缴账单（免登录） */
    @GetMapping("/company-bills")
    public Result<Map<String, Object>> companyBills(@RequestParam Long companyId,
                                                    @RequestParam String phone) {
        PropertyOwner owner = ownerRepository.findByOwnerPhone(phone)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("该手机号未绑定业主，请确认是否已登记"));
        java.util.List<PropertyBill> bills = billRepository.findByOwnerIdAndCompanyIdAndStatusInOrderByDueDateAsc(owner.getOwnerId(), companyId, java.util.Collections.singletonList(0));
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        java.math.BigDecimal total = BigDecimal.ZERO;
        for (PropertyBill b : bills) {
            BigDecimal remaining = nz(b.getAmount()).subtract(nz(b.getDeducted())).subtract(nz(b.getPaid()));
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("billId", b.getBillId());
            m.put("billNo", b.getBillNo());
            m.put("billPeriod", b.getBillPeriod());
            m.put("billType", b.getBillType());
            m.put("remaining", remaining);
            total = total.add(remaining);
            list.add(m);
        }
        PropertyCompany company = companyRepository.findById(companyId).orElse(null);
        PropertyWallet w = walletRepository.findByOwnerIdAndCompanyId(owner.getOwnerId(), companyId).orElse(null);
        Map<String, Object> out = new HashMap<>();
        out.put("ownerName", owner.getOwnerName());
        out.put("companyName", company == null ? "物业公司" : company.getCompanyName());
        out.put("bills", list);
        out.put("totalDue", total);
        out.put("walletBalance", w == null ? BigDecimal.ZERO : w.getBalance());
        return Result.ok(out);
    }

    /** 公司级扫码：开盲盒（按 property_prize_pool 权重随机，免登录，仅展示中奖结果） */
    @PostMapping("/draw")
    public Result<Map<String, Object>> draw(@RequestParam Long companyId,
                                             @RequestParam String phone) {
        PropertyOwner owner = ownerRepository.findByOwnerPhone(phone)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("该手机号未绑定业主"));
        java.util.List<PropertyPrizePool> pool = prizePoolRepository.findByCompanyIdOrderByChannelAscPoolTypeAscCreateTimeDesc(companyId);
        java.util.List<PropertyPrizePool> enabled = new java.util.ArrayList<>();
        int totalWeight = 0;
        for (PropertyPrizePool p : pool) {
            if (p.getEnabled() != null && p.getEnabled() == 1) {
                enabled.add(p);
                totalWeight += (p.getWeight() == null ? 1 : p.getWeight());
            }
        }
        if (enabled.isEmpty() || totalWeight <= 0) {
            throw new com.ibigou.blindbox.common.BizException("该公司暂未配置开奖奖品");
        }
        int r = (int) (Math.random() * totalWeight);
        int acc = 0;
        PropertyPrizePool hit = enabled.get(enabled.size() - 1);
        for (PropertyPrizePool p : enabled) {
            acc += (p.getWeight() == null ? 1 : p.getWeight());
            if (r < acc) { hit = p; break; }
        }
        String[] typeNames = {"普通商品", "折扣券", "立减券", "普通余额", "团购免单余额"};
        Map<String, Object> m = new HashMap<>();
        m.put("prizeId", hit.getPrizeId());
        m.put("remark", hit.getRemark());
        m.put("prizeType", hit.getPrizeType());
        m.put("prizeTypeName", typeNames[hit.getPrizeType() == null ? 0 : Math.min(hit.getPrizeType(), 4)]);
        m.put("prizeValue", hit.getPrizeValue());
        // 余额类奖品（3普通余额 4团购免单余额）自动入账业主物业钱包
        Integer ptype = hit.getPrizeType();
        if (ptype != null && (ptype == 3 || ptype == 4) && hit.getPrizeValue() != null
                && hit.getPrizeValue().compareTo(BigDecimal.ZERO) > 0) {
            PropertyWallet w = walletRepository.findByOwnerIdAndCompanyId(owner.getOwnerId(), companyId)
                    .orElseGet(() -> {
                        PropertyWallet nw = new PropertyWallet();
                        nw.setOwnerId(owner.getOwnerId());
                        nw.setCompanyId(companyId);
                        nw.setBalance(BigDecimal.ZERO);
                        return nw;
                    });
            w.setBalance(w.getBalance().add(hit.getPrizeValue()));
            w.setUpdateTime(java.time.LocalDateTime.now());
            walletRepository.save(w);
            m.put("credited", true);
            m.put("walletBalance", w.getBalance());
        } else {
            PropertyWallet w = walletRepository.findByOwnerIdAndCompanyId(owner.getOwnerId(), companyId).orElse(null);
            m.put("credited", false);
            m.put("walletBalance", w == null ? BigDecimal.ZERO : w.getBalance());
        }
        return Result.ok(m);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
