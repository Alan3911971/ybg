package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.PropertyDeviceAlert;
import com.ibigou.blindbox.service.PropertyDeviceAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * IoT 设备告警 Webhook（无需鉴权，供设备/中间件直接调用）。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/iot")
@RequiredArgsConstructor
public class PropertyIotWebhookController {

    private final PropertyDeviceAlertService deviceAlertService;

    /**
     * 接收设备告警上报
     * Body: { "deviceNo": "xxx", "alertType": "offline", "message": "...", "rawData": "..." }
     */
    @PostMapping("/alert-receive")
    public Result<?> receiveAlert(@RequestBody Map<String, String> body) {
        String deviceNo = body.get("deviceNo");
        String alertType = body.get("alertType");
        String message = body.get("message");
        String rawData = body.get("rawData");

        if (deviceNo == null || deviceNo.isBlank()) {
            return Result.fail("deviceNo is required");
        }
        if (alertType == null || alertType.isBlank()) {
            return Result.fail("alertType is required");
        }
        if (message == null || message.isBlank()) {
            message = alertType + " alert from " + deviceNo;
        }

        log.info("IoT alert received: deviceNo={}, alertType={}", deviceNo, alertType);

        try {
            PropertyDeviceAlert alert = deviceAlertService.processAlert(deviceNo, alertType, message, rawData);
            if (alert == null) {
                // 被静默期过滤
                return Result.ok(Map.of("status", "silenced"));
            }
            return Result.ok(Map.of("status", "created", "alertId", alert.getId()));
        } catch (Exception e) {
            log.error("Failed to process IoT alert: deviceNo={}, alertType={}", deviceNo, alertType, e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 设备心跳上报
     * Body: { "deviceNo": "xxx" }
     */
    @PostMapping("/heartbeat")
    public Result<?> heartbeat(@RequestBody Map<String, String> body) {
        String deviceNo = body.get("deviceNo");
        if (deviceNo == null || deviceNo.isBlank()) {
            return Result.fail("deviceNo is required");
        }
        deviceAlertService.updateDeviceHeartbeat(deviceNo);
        return Result.ok(Map.of("status", "ok"));
    }
}
