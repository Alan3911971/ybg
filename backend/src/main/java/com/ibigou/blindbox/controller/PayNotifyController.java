package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.service.WxPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 微信支付回调（平台收商家年费）。
 * 微信服务器调用 /api/pay/wx/notify；验签解密后确认续费订单。
 */
@RestController
@RequestMapping("/api/pay/wx")
@RequiredArgsConstructor
public class PayNotifyController {

    private final WxPayService wxPayService;

    @PostMapping("/notify")
    public ResponseEntity<String> notify(@RequestBody String body,
                                         @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                         @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                         @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                         @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        try {
            String result = wxPayService.handleNotify(body, signature, timestamp, nonce, serial);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"code\":\"FAIL\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}
