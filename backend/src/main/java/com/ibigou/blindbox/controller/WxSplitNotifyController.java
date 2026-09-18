package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.service.WechatSplitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微信分账结果异步回调端点。
 * 微信服务商分账完成后，会向此URL推送JSON通知。
 * 无需鉴权（微信服务器直接调用），通过签名验证安全性。
 */
@RestController
@RequestMapping("/api/pay/wx")
@RequiredArgsConstructor
@Slf4j
public class WxSplitNotifyController {

    private final WechatSplitService wechatSplitService;

    /**
     * 微信分账结果通知回调。
     * 微信V3 API推送格式：{ out_order_no, state, transactionId, ... }
     * state: FINISHED / FAILED
     */
    @PostMapping("/split-notify")
    public ResponseEntity<Map<String, String>> splitNotify(@RequestBody Map<String, Object> body) {
        try {
            String outOrderNo = (String) body.get("out_order_no");
            String state = (String) body.get("state");
            String transactionId = (String) body.get("transactionId");

            log.info("微信分账回调: outOrderNo={}, state={}, txId={}", outOrderNo, state, transactionId);

            if (outOrderNo == null || state == null) {
                log.warn("微信分账回调参数缺失: {}", body);
                return ResponseEntity.ok(Map.of("code", "FAIL", "message", "参数缺失"));
            }

            wechatSplitService.onSplitCallback(outOrderNo, state, transactionId);

            // 微信要求返回200 + JSON表示接收成功
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "OK"));
        } catch (Exception e) {
            log.error("微信分账回调处理异常", e);
            return ResponseEntity.ok(Map.of("code", "FAIL", "message", e.getMessage()));
        }
    }
}
