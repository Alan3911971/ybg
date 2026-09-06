package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.OfflineOrder;
import com.ibigou.blindbox.repository.MerchantRepository;
import com.ibigou.blindbox.repository.OfflineOrderRepository;
import com.ibigou.blindbox.service.AlipayService;
import com.ibigou.blindbox.service.GlobalConfigService;
import com.ibigou.blindbox.service.OfflineOrderService;
import com.ibigou.blindbox.service.WxPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户支付接口（支持静态收款码和API支付两种模式）。
 * 支付模式由商家 pay_mode 字段决定：0=静态收款码，1=API支付。
 */
@RestController
@RequestMapping("/api/customer/pay")
@RequiredArgsConstructor
public class CustomerPayController {

    private final MerchantRepository merchantRepository;
    private final OfflineOrderRepository orderRepository;
    private final OfflineOrderService offlineOrderService;
    private final WxPayService wxPayService;
    private final AlipayService alipayService;
    private final GlobalConfigService configService;

    /**
     * 创建支付订单。
     * 如果商家 pay_mode=0（静态码），返回商家收款码图片URL。
     * 如果商家 pay_mode=1（API支付），调用微信/支付宝下单，返回支付二维码。
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> createPay(@RequestParam String orderNo,
                                                  @RequestParam String payType) {
        OfflineOrder order = orderRepository.findById(orderNo)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("订单不存在"));
        Merchant merchant = merchantRepository.findById(order.getMerchantNo())
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("商家不存在"));

        Map<String, Object> result = new HashMap<>();
        // 支付模式：优先商家pay_mode(1=API)，否则用全局customer_pay_mode
        int merchantPayMode = merchant.getPayMode() == null ? 0 : merchant.getPayMode();
        int globalPayMode = Integer.parseInt(configService.get("customer_pay_mode", "0"));
        int payMode = (merchantPayMode == 1 || globalPayMode == 1) ? 1 : 0;
        result.put("payMode", payMode);
        result.put("merchantPayMode", merchantPayMode);
        result.put("globalPayMode", globalPayMode);
        result.put("payType", payType);
        result.put("orderNo", orderNo);
        result.put("amount", order.getPayAmount());

        if (payMode == 0) {
            // 静态收款码模式：返回商家上传的收款码图片
            String qrUrl = null;
            String label = "商家收款码";
            if ("wechat".equals(payType)) {
                qrUrl = merchant.getReceiveQrImgWechat();
                label = "微信收款码";
            } else if ("alipay".equals(payType)) {
                qrUrl = merchant.getReceiveQrImgAlipay();
                label = "支付宝收款码";
            } else if ("unionpay".equals(payType)) {
                qrUrl = merchant.getReceiveQrImgUnionpay();
                label = "云闪付收款码";
            } else {
                qrUrl = merchant.getReceiveQrImgOther();
                label = "商家聚合收款码";
            }
            result.put("qrUrl", qrUrl);
            result.put("label", label);
            result.put("realPay", false);
            return Result.ok(result);
        } else {
            // API支付模式：调用微信/支付宝下单
            String notifyUrl = configService.get("IBIGOU_DOMAIN", "https://ybgtc.com");
            String qrCode = null;
            boolean realPay = false;
            if ("wechat".equals(payType)) {
                qrCode = wxPayService.nativePay(orderNo, order.getPayAmount(),
                        "宜必购订单-" + orderNo, notifyUrl + "/api/pay/wx/notify");
                realPay = qrCode != null;
            } else if ("alipay".equals(payType)) {
                qrCode = alipayService.precreate(orderNo, order.getPayAmount(),
                        "宜必购订单-" + orderNo, notifyUrl + "/api/pay/alipay/notify");
                realPay = qrCode != null;
            }
            result.put("qrCode", qrCode);
            result.put("realPay", realPay);
            result.put("label", "wechat".equals(payType) ? "微信支付" : "支付宝支付");
            return Result.ok(result);
        }
    }

    /**
     * 查询支付状态（API支付模式下轮询用）。
     */
    @GetMapping("/query")
    public Result<Map<String, Object>> queryPay(@RequestParam String orderNo) {
        OfflineOrder order = orderRepository.findById(orderNo)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("订单不存在"));
        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo);
        result.put("orderStatus", order.getOrderStatus());
        result.put("paid", order.getOrderStatus() != null && order.getOrderStatus() == 1);
        return Result.ok(result);
    }
}
