package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.service.AlipayService;
import com.ibigou.blindbox.service.WxPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付回调（微信V2 + 支付宝）。
 * 微信服务器调用 /api/pay/wx/notify（XML body）；支付宝服务器调用 /api/pay/alipay/notify（form参数）。
 */
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayNotifyController {

    private final WxPayService wxPayService;
    private final AlipayService alipayService;

    @PostMapping(value = "/wx/notify", produces = "application/xml; charset=UTF-8")
    public ResponseEntity<String> wxNotify(@RequestBody String body) {
        try {
            String result = wxPayService.handleNotify(body);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[" + e.getMessage() + "]]></return_msg></xml>");
        }
    }

    @PostMapping("/alipay/notify")
    public ResponseEntity<String> alipayNotify(@RequestParam Map<String, String> params) {
        try {
            String result = alipayService.handleNotify(params);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("fail");
        }
    }
}
