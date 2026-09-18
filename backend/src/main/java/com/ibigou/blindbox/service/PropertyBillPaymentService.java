package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyBill;
import com.ibigou.blindbox.entity.PropertyParkingFee;
import com.ibigou.blindbox.entity.PropertyPayment;
import com.ibigou.blindbox.repository.PropertyBillRepository;
import com.ibigou.blindbox.repository.PropertyParkingFeeRepository;
import com.ibigou.blindbox.repository.PropertyPaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 物业账单在线缴费：
 * 业主选择账单 → 创建支付单（PB-前缀，走平台微信/支付宝商户号）→ 支付回调确认 → 账单置为已缴。
 * 注意：WxPayService/AlipayService 在回调路由中也依赖本服务，存在循环引用，
 * 故此处用 @Lazy 注入两个支付服务，Spring 注入懒代理打破循环。
 */
@Service
@Slf4j
public class PropertyBillPaymentService {

    private final PropertyPaymentRepository paymentRepository;
    private final PropertyBillRepository billRepository;
    private final PropertyParkingFeeRepository parkingFeeRepository;
    private final WxPayService wxPayService;
    private final AlipayService alipayService;
    private final GlobalConfigService configService;

    private static final Random RANDOM = new Random();

    public PropertyBillPaymentService(PropertyPaymentRepository paymentRepository,
                                      PropertyBillRepository billRepository,
                                      PropertyParkingFeeRepository parkingFeeRepository,
                                      @Lazy WxPayService wxPayService,
                                      @Lazy AlipayService alipayService,
                                      GlobalConfigService configService) {
        this.paymentRepository = paymentRepository;
        this.billRepository = billRepository;
        this.parkingFeeRepository = parkingFeeRepository;
        this.wxPayService = wxPayService;
        this.alipayService = alipayService;
        this.configService = configService;
    }

    /**
     * 创建支付单：校验账单归属与状态 → 关闭旧待支付单 → 生成 PB- 支付单 → 微信/支付宝下单返回二维码。
     */
    @Transactional
    public Map<String, Object> createPayment(Long ownerId, Long billId, String channel) {
        PropertyBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new BizException("账单不存在"));
        if (!bill.getOwnerId().equals(ownerId)) {
            throw new BizException("无权操作");
        }
        if (bill.getStatus() != null && bill.getStatus() == 1) {
            throw new BizException("账单已缴清");
        }
        BigDecimal amount = nz(bill.getAmount());
        BigDecimal deducted = nz(bill.getDeducted());
        BigDecimal paid = nz(bill.getPaid());
        BigDecimal payable = amount.subtract(deducted).subtract(paid);
        if (payable.signum() <= 0) {
            throw new BizException("账单已结清，无需缴费");
        }
        if (!"wechat".equals(channel) && !"alipay".equals(channel)) {
            throw new BizException("不支持的支付方式");
        }

        // 关闭该账单历史待支付单，避免重复支付
        List<PropertyPayment> oldPending = paymentRepository.findByBillIdAndFeeTypeAndStatus(billId, 0, 0);
        oldPending.forEach(p -> p.setStatus(2));
        paymentRepository.saveAll(oldPending);

        // 生成支付单
        String paymentNo = "PB" + System.currentTimeMillis() + (1000 + RANDOM.nextInt(9000));
        PropertyPayment payment = new PropertyPayment();
        payment.setPaymentNo(paymentNo);
        payment.setOwnerId(ownerId);
        payment.setBillId(billId);
        payment.setBillNo(bill.getBillNo());
        payment.setCompanyId(bill.getCompanyId());
        payment.setRoomId(bill.getRoomId());
        payment.setAmount(payable);
        payment.setChannel(channel);
        payment.setStatus(0);
        payment.setFeeType(0);
        paymentRepository.save(payment);

        String notifyUrl = configService.get("IBIGOU_DOMAIN", "https://ybgtc.com");
        String desc = "物业费-" + (bill.getBillPeriod() == null ? billId : bill.getBillPeriod());
        Map<String, Object> result = new HashMap<>();
        result.put("paymentNo", paymentNo);
        result.put("amount", payable);
        result.put("billNo", bill.getBillNo());
        result.put("billPeriod", bill.getBillPeriod());
        result.put("status", 0);

        if ("wechat".equals(channel)) {
            String qrCode = wxPayService.nativePay(paymentNo, payable, desc, notifyUrl + "/api/pay/wx/notify");
            if (qrCode == null) {
                throw new BizException("微信支付下单失败，请稍后重试");
            }
            result.put("qrCode", qrCode);
        } else {
            String qrCode = alipayService.precreate(paymentNo, payable, desc, notifyUrl + "/api/pay/alipay/notify");
            if (qrCode == null) {
                throw new BizException("支付宝支付下单失败，请稍后重试");
            }
            result.put("qrCode", qrCode);
        }
        log.info("物业账单支付单创建: paymentNo={}, billId={}, amount={}, channel={}", paymentNo, billId, payable, channel);
        return result;
    }

    /**
     * 支付回调确认（微信/支付宝 notify 路由 PB- 前缀调用）。幂等：已确认直接返回。
     */
    @Transactional
    public void confirmPaid(String paymentNo, String channel) {
        PropertyPayment payment = paymentRepository.findByPaymentNo(paymentNo).orElse(null);
        if (payment == null) {
            log.warn("物业支付单不存在，忽略回调: {}", paymentNo);
            return;
        }
        if (payment.getStatus() != null && payment.getStatus() == 1) {
            log.info("物业支付单已确认，跳过重复回调: {}", paymentNo);
            return;
        }
        payment.setStatus(1);
        payment.setPayTime(LocalDateTime.now());
        if (channel != null && !channel.isBlank()) {
            payment.setChannel(channel);
        }
        paymentRepository.save(payment);

        // 按缴费类型更新账单（物业费 feeType=0 / 车位费 feeType=1）
        Integer feeType = payment.getFeeType() == null ? 0 : payment.getFeeType();
        if (feeType == 1) {
            PropertyParkingFee fee = parkingFeeRepository.findById(payment.getBillId()).orElse(null);
            if (fee != null) {
                BigDecimal amount = nz(fee.getAmount());
                BigDecimal deducted = nz(fee.getDeducted());
                BigDecimal paidOld = nz(fee.getPaid());
                fee.setPaid(paidOld.add(payment.getAmount()));
                if (fee.getPaid().add(deducted).compareTo(amount) >= 0) {
                    fee.setStatus(1);
                }
                parkingFeeRepository.save(fee);
                log.info("车位费支付确认: paymentNo={}, feeId={}, feePaid={}, feeStatus={}",
                        paymentNo, payment.getBillId(), fee.getPaid(), fee.getStatus());
            }
        } else {
            PropertyBill bill = billRepository.findById(payment.getBillId()).orElse(null);
            if (bill != null) {
                BigDecimal amount = nz(bill.getAmount());
                BigDecimal deducted = nz(bill.getDeducted());
                BigDecimal paidOld = nz(bill.getPaid());
                bill.setPaid(paidOld.add(payment.getAmount()));
                if (bill.getPaid().add(deducted).compareTo(amount) >= 0) {
                    bill.setStatus(1);
                }
                billRepository.save(bill);
                log.info("物业账单支付确认: paymentNo={}, billId={}, billPaid={}, billStatus={}",
                        paymentNo, payment.getBillId(), bill.getPaid(), bill.getStatus());
            }
        }
    }

    /**
     * 查询支付单状态（前端轮询用）。
     */
    public Map<String, Object> queryStatus(Long ownerId, String paymentNo) {
        PropertyPayment payment = paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BizException("支付单不存在"));
        if (!payment.getOwnerId().equals(ownerId)) {
            throw new BizException("无权查看");
        }
        Map<String, Object> m = new HashMap<>();
        m.put("paymentNo", paymentNo);
        m.put("status", payment.getStatus());
        m.put("paid", payment.getStatus() != null && payment.getStatus() == 1);
        return m;
    }

    /**
     * 车位费在线缴费：与物业费同一套支付单/回调，feeType=1，回调确认更新 parking_fee。
     */
    @Transactional
    public Map<String, Object> createParkingPayment(Long ownerId, Long feeId, String channel) {
        PropertyParkingFee fee = parkingFeeRepository.findById(feeId)
                .orElseThrow(() -> new BizException("车位费账单不存在"));
        if (!fee.getOwnerId().equals(ownerId)) {
            throw new BizException("无权操作");
        }
        if (fee.getStatus() != null && fee.getStatus() == 1) {
            throw new BizException("车位费已缴清");
        }
        BigDecimal amount = nz(fee.getAmount());
        BigDecimal deducted = nz(fee.getDeducted());
        BigDecimal paid = nz(fee.getPaid());
        BigDecimal payable = amount.subtract(deducted).subtract(paid);
        if (payable.signum() <= 0) {
            throw new BizException("车位费已结清，无需缴费");
        }
        if (!"wechat".equals(channel) && !"alipay".equals(channel)) {
            throw new BizException("不支持的支付方式");
        }
        List<PropertyPayment> oldPending = paymentRepository.findByBillIdAndFeeTypeAndStatus(feeId, 1, 0);
        oldPending.forEach(p -> p.setStatus(2));
        paymentRepository.saveAll(oldPending);

        String paymentNo = "PB" + System.currentTimeMillis() + (1000 + RANDOM.nextInt(9000));
        PropertyPayment payment = new PropertyPayment();
        payment.setPaymentNo(paymentNo);
        payment.setOwnerId(ownerId);
        payment.setBillId(feeId);
        payment.setBillNo(fee.getFeeNo());
        payment.setCompanyId(fee.getCompanyId());
        payment.setRoomId(fee.getRoomId());
        payment.setAmount(payable);
        payment.setChannel(channel);
        payment.setStatus(0);
        payment.setFeeType(1);
        paymentRepository.save(payment);

        String notifyUrl = configService.get("IBIGOU_DOMAIN", "https://ybgtc.com");
        String desc = "车位费-" + (fee.getPeriod() == null ? feeId : fee.getPeriod());
        Map<String, Object> result = new HashMap<>();
        result.put("paymentNo", paymentNo);
        result.put("amount", payable);
        result.put("billNo", fee.getFeeNo());
        result.put("billPeriod", fee.getPeriod());
        result.put("status", 0);

        if ("wechat".equals(channel)) {
            String qrCode = wxPayService.nativePay(paymentNo, payable, desc, notifyUrl + "/api/pay/wx/notify");
            if (qrCode == null) {
                throw new BizException("微信支付下单失败，请稍后重试");
            }
            result.put("qrCode", qrCode);
        } else {
            String qrCode = alipayService.precreate(paymentNo, payable, desc, notifyUrl + "/api/pay/alipay/notify");
            if (qrCode == null) {
                throw new BizException("支付宝支付下单失败，请稍后重试");
            }
            result.put("qrCode", qrCode);
        }
        log.info("车位费支付单创建: paymentNo={}, feeId={}, amount={}, channel={}", paymentNo, feeId, payable, channel);
        return result;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
