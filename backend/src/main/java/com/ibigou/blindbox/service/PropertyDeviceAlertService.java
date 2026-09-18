package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyAlertRule;
import com.ibigou.blindbox.entity.PropertyDevice;
import com.ibigou.blindbox.entity.PropertyDeviceAlert;
import com.ibigou.blindbox.entity.PropertyWorkorder;
import com.ibigou.blindbox.repository.PropertyAlertRuleRepository;
import com.ibigou.blindbox.repository.PropertyDeviceAlertRepository;
import com.ibigou.blindbox.repository.PropertyDeviceRepository;
import com.ibigou.blindbox.repository.PropertyWorkorderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyDeviceAlertService {

    private final PropertyDeviceRepository deviceRepository;
    private final PropertyAlertRuleRepository alertRuleRepository;
    private final PropertyDeviceAlertRepository deviceAlertRepository;
    private final PropertyWorkorderRepository workorderRepository;

    /**
     * 处理设备告警上报：查找设备 → 匹配规则 → 检查静默期 → 创建告警 → 可选自动创建工单
     */
    @Transactional
    public PropertyDeviceAlert processAlert(String deviceNo, String alertType, String message, String rawData) {
        // 1. 查找设备
        PropertyDevice device = deviceRepository.findByDeviceNo(deviceNo)
                .orElseThrow(() -> new BizException("设备不存在: " + deviceNo));

        // 2. 匹配告警规则（按设备类型 + 告警类型，或全局规则）
        List<PropertyAlertRule> rules = alertRuleRepository.findByDeviceTypeAndAlertTypeAndStatus(
                device.getDeviceType(), alertType, 1);
        // 也查找 deviceType=null 的全局规则
        List<PropertyAlertRule> globalRules = alertRuleRepository.findByDeviceTypeAndAlertTypeAndStatus(
                null, alertType, 1);
        rules.addAll(globalRules);

        PropertyAlertRule matchedRule = null;
        if (!rules.isEmpty()) {
            matchedRule = rules.get(0);
        }

        // 3. 检查静默期
        if (matchedRule != null && matchedRule.getSilenceMinutes() != null && matchedRule.getSilenceMinutes() > 0) {
            LocalDateTime silenceSince = LocalDateTime.now().minusMinutes(matchedRule.getSilenceMinutes());
            List<PropertyDeviceAlert> recentAlerts = deviceAlertRepository
                    .findByCompanyIdAndCreateTimeAfter(device.getCompanyId(), silenceSince);
            boolean silenced = recentAlerts.stream()
                    .anyMatch(a -> a.getDeviceId().equals(device.getId())
                            && alertType.equals(a.getAlertType()));
            if (silenced) {
                log.info("Alert silenced for device={}, alertType={}, within {}min",
                        deviceNo, alertType, matchedRule.getSilenceMinutes());
                return null;
            }
        }

        // 4. 确定告警级别
        int level = determineLevel(alertType);

        // 5. 创建告警记录
        PropertyDeviceAlert alert = new PropertyDeviceAlert();
        alert.setDeviceId(device.getId());
        alert.setCompanyId(device.getCompanyId());
        alert.setAlertType(alertType);
        alert.setLevel(level);
        alert.setMessage(message);
        alert.setRawData(rawData);
        alert.setRuleId(matchedRule != null ? matchedRule.getId() : null);
        alert.setAckStatus(0);
        PropertyDeviceAlert saved = deviceAlertRepository.save(alert);

        log.info("Device alert created: deviceId={}, alertType={}, level={}",
                device.getId(), alertType, level);

        // 6. 自动创建工单
        if (matchedRule != null && matchedRule.getAutoWorkorder() != null && matchedRule.getAutoWorkorder() == 1) {
            try {
                PropertyWorkorder wo = new PropertyWorkorder();
                wo.setWoNo("WO" + System.currentTimeMillis());
                wo.setOwnerId(0L); // 系统自动创建，无业主
                wo.setCompanyId(device.getCompanyId());
                wo.setWoType(1); // 报修
                wo.setTitle("[设备告警] " + device.getDeviceName() + " - " + message);
                wo.setDescription("设备编号: " + deviceNo + "\n告警类型: " + alertType + "\n告警内容: " + message);
                wo.setUrgency(level >= 3 ? 3 : (level >= 2 ? 2 : 1));
                wo.setCategory("设备告警");
                wo.setStatus(0);
                wo.setTimeoutCount(0);
                PropertyWorkorder savedWo = workorderRepository.save(wo);

                saved.setWorkorderId(savedWo.getWoId());
                deviceAlertRepository.save(saved);

                log.info("Auto workorder created for alert: alertId={}, woId={}",
                        saved.getId(), savedWo.getWoId());
            } catch (Exception e) {
                log.error("Failed to auto-create workorder for alert: {}", saved.getId(), e);
            }
        }

        return saved;
    }

    /**
     * 确认告警
     */
    @Transactional
    public void acknowledgeAlert(Long alertId, Long adminId) {
        PropertyDeviceAlert alert = deviceAlertRepository.findById(alertId)
                .orElseThrow(() -> new BizException("告警记录不存在"));
        alert.setAckStatus(1);
        alert.setAckBy(adminId);
        alert.setAckTime(LocalDateTime.now());
        deviceAlertRepository.save(alert);
        log.info("Alert acknowledged: alertId={}, adminId={}", alertId, adminId);
    }

    /**
     * 处理完成告警
     */
    @Transactional
    public void resolveAlert(Long alertId, Long adminId) {
        PropertyDeviceAlert alert = deviceAlertRepository.findById(alertId)
                .orElseThrow(() -> new BizException("告警记录不存在"));
        alert.setAckStatus(2);
        alert.setAckBy(adminId);
        alert.setAckTime(LocalDateTime.now());
        deviceAlertRepository.save(alert);
        log.info("Alert resolved: alertId={}, adminId={}", alertId, adminId);
    }

    /**
     * 获取告警统计
     */
    public Map<String, Object> getAlertStats(Long companyId) {
        Map<String, Object> stats = new HashMap<>();

        // 未确认数量
        long unackedCount = deviceAlertRepository.countByCompanyIdAndAckStatus(companyId, 0);
        stats.put("unackedCount", unackedCount);

        // 今日告警数
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<PropertyDeviceAlert> todayAlerts = deviceAlertRepository
                .findByCompanyIdAndCreateTimeAfter(companyId, todayStart);
        stats.put("todayCount", todayAlerts.size());

        // 严重告警数（level=3 且未确认）
        long criticalCount = todayAlerts.stream()
                .filter(a -> a.getLevel() != null && a.getLevel() == 3 && a.getAckStatus() != null && a.getAckStatus() == 0)
                .count();
        stats.put("criticalCount", criticalCount);

        return stats;
    }

    /**
     * 更新设备心跳
     */
    @Transactional
    public void updateDeviceHeartbeat(String deviceNo) {
        PropertyDevice device = deviceRepository.findByDeviceNo(deviceNo).orElse(null);
        if (device == null) {
            log.warn("Heartbeat for unknown device: {}", deviceNo);
            return;
        }
        device.setLastHeartbeat(LocalDateTime.now());
        // 如果之前是离线状态，恢复为在线
        if (device.getStatus() != null && device.getStatus() == 0) {
            device.setStatus(1);
            log.info("Device back online: {}", deviceNo);
        }
        deviceRepository.save(device);
    }

    /**
     * 根据告警类型确定级别
     */
    private int determineLevel(String alertType) {
        if ("fault".equals(alertType) || "tamper".equals(alertType)) {
            return 3; // critical
        } else if ("offline".equals(alertType) || "overtemp".equals(alertType)) {
            return 2; // warning
        }
        return 1; // info
    }
}
