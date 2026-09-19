package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.PropertyBill;
import com.ibigou.blindbox.entity.PropertyCompany;
import com.ibigou.blindbox.entity.PropertyOwner;
import com.ibigou.blindbox.repository.PropertyBillRepository;
import com.ibigou.blindbox.repository.PropertyCompanyRepository;
import com.ibigou.blindbox.repository.PropertyOwnerRepository;
import com.ibigou.blindbox.repository.PropertyPaymentRepository;
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

    /** 手机号验证 + 创建支付单（微信/支付宝），返回 qrCode 供页面轮询展示 */
    @PostMapping("/pay")
    public Result<Map<String, Object>> pay(@RequestParam Long billId,
                                           @RequestParam String phone,
                                           @RequestParam String channel) {
        PropertyOwner owner = ownerRepository.findByOwnerPhone(phone)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("该手机号未绑定业主，请确认是否已登记"));
        Map<String, Object> r = propertyBillPaymentService.createPayment(owner.getOwnerId(), billId, channel);
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

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
