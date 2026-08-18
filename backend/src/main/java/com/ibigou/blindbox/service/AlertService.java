package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.SysAlert;
import com.ibigou.blindbox.repository.SysAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * P2 系统告警：未处理异常/定时任务失败记录，平台后台查看处理。
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private final SysAlertRepository alertRepository;

    @Transactional
    public void record(String type, String content) {
        try {
            SysAlert alert = new SysAlert();
            alert.setAlertType(type);
            alert.setContent(content.length() > 490 ? content.substring(0, 490) : content);
            alert.setStatus(0);
            alert.setCreateTime(LocalDateTime.now());
            alertRepository.save(alert);
        } catch (Exception ignore) {
            // 告警写入失败不阻塞主流程
        }
    }

    public List<SysAlert> list() {
        return alertRepository.findByOrderByAlertIdDesc();
    }

    public long unhandledCount() {
        return alertRepository.findAll().stream().filter(a -> a.getStatus() == 0).count();
    }

    @Transactional
    public void markHandled(Long alertId) {
        alertRepository.findById(alertId).ifPresent(a -> {
            a.setStatus(1);
            alertRepository.save(a);
        });
    }
}
