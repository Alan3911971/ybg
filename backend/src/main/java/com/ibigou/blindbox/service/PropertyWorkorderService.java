package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyWoLog;
import com.ibigou.blindbox.entity.PropertyWorkorder;
import com.ibigou.blindbox.repository.PropertyCompanyRepository;
import com.ibigou.blindbox.repository.PropertyOwnerRepository;
import com.ibigou.blindbox.repository.PropertyWoLogRepository;
import com.ibigou.blindbox.repository.PropertyWorkorderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyWorkorderService {

    private final PropertyWorkorderRepository workorderRepository;
    private final PropertyWoLogRepository woLogRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyCompanyRepository companyRepository;

    @Transactional
    public PropertyWorkorder createWorkorder(Long ownerId, Long companyId, Integer woType,
                                             String title, String description, String images,
                                             Integer urgency, String category) {
        PropertyWorkorder wo = new PropertyWorkorder();
        wo.setWoNo("WO" + System.currentTimeMillis());
        wo.setOwnerId(ownerId);
        wo.setCompanyId(companyId);
        wo.setWoType(woType);
        wo.setTitle(title);
        wo.setDescription(description);
        wo.setImages(images);
        wo.setUrgency(urgency);
        wo.setCategory(category);
        wo.setStatus(0);
        wo.setTimeoutCount(0);
        PropertyWorkorder saved = workorderRepository.save(wo);
        logAction(saved, "CREATE", null, null);
        log.info("Workorder created: woNo={}, ownerId={}", saved.getWoNo(), ownerId);
        return saved;
    }

    @Transactional
    public void assignWorkorder(Long woId, Long assigneeId, String assigneeName, String operatorName) {
        PropertyWorkorder wo = workorderRepository.findById(woId)
                .orElseThrow(() -> new BizException("工单不存在"));
        if (wo.getStatus() != 0 && wo.getStatus() != 5) {
            throw new BizException("当前状态不允许接单");
        }
        wo.setAssigneeId(assigneeId);
        wo.setAssigneeName(assigneeName);
        wo.setStatus(1);
        workorderRepository.save(wo);
        logAction(wo, "ASSIGN", operatorName, null);
        log.info("Workorder assigned: woId={}, assigneeId={}", woId, assigneeId);
    }

    @Transactional
    public void transferWorkorder(Long woId, Long newAssigneeId, String newAssigneeName,
                                  String reason, String operatorName) {
        PropertyWorkorder wo = workorderRepository.findById(woId)
                .orElseThrow(() -> new BizException("工单不存在"));
        if (wo.getStatus() != 1) {
            throw new BizException("只有处理中的工单才能转派");
        }
        wo.setAssigneeId(newAssigneeId);
        wo.setAssigneeName(newAssigneeName);
        workorderRepository.save(wo);
        logAction(wo, "TRANSFER", operatorName, reason);
        log.info("Workorder transferred: woId={}, newAssigneeId={}", woId, newAssigneeId);
    }

    @Transactional
    public void completeWorkorder(Long woId, String operatorName) {
        PropertyWorkorder wo = workorderRepository.findById(woId)
                .orElseThrow(() -> new BizException("工单不存在"));
        if (wo.getStatus() != 1) {
            throw new BizException("只有处理中的工单才能完成");
        }
        wo.setStatus(2);
        wo.setCompletedTime(LocalDateTime.now());
        workorderRepository.save(wo);
        logAction(wo, "COMPLETE", operatorName, null);
        log.info("Workorder completed: woId={}", woId);
    }

    @Transactional
    public void rateWorkorder(Long woId, Integer rating, String comment, Long ownerId) {
        PropertyWorkorder wo = workorderRepository.findById(woId)
                .orElseThrow(() -> new BizException("工单不存在"));
        if (wo.getStatus() != 2) {
            throw new BizException("只有待验收的工单才能评价");
        }
        if (!wo.getOwnerId().equals(ownerId)) {
            throw new BizException("只有工单发起人才能评价");
        }
        wo.setRating(rating);
        wo.setRatingComment(comment);
        wo.setRatedTime(LocalDateTime.now());
        wo.setStatus(3);
        workorderRepository.save(wo);
        logAction(wo, "RATE", null, "rating=" + rating);
        log.info("Workorder rated: woId={}, rating={}", woId, rating);
    }

    @Transactional
    public void closeWorkorder(Long woId, String operatorName) {
        PropertyWorkorder wo = workorderRepository.findById(woId)
                .orElseThrow(() -> new BizException("工单不存在"));
        wo.setStatus(4);
        workorderRepository.save(wo);
        logAction(wo, "CLOSE", operatorName, null);
        log.info("Workorder closed: woId={}", woId);
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void checkTimeoutEscalation() {
        List<PropertyWorkorder> all = workorderRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        int escalated = 0;
        for (PropertyWorkorder wo : all) {
            if (wo.getStatus() == 1 && wo.getCreateTime() != null) {
                long minutes = Duration.between(wo.getCreateTime(), now).toMinutes();
                if (minutes > 120 && wo.getTimeoutCount() < 3) {
                    wo.setTimeoutCount(wo.getTimeoutCount() + 1);
                    workorderRepository.save(wo);
                    logAction(wo, "ESCALATE", "SYSTEM", "timeout=" + minutes + "min, count=" + wo.getTimeoutCount());
                    escalated++;
                }
            }
        }
        if (escalated > 0) {
            log.info("Timeout escalation check completed: {} workorders escalated", escalated);
        }
    }

    private void logAction(PropertyWorkorder wo, String action, String operatorName, String remark) {
        PropertyWoLog logEntry = new PropertyWoLog();
        logEntry.setWoId(wo.getWoId());
        logEntry.setAction(action);
        logEntry.setOperatorName(operatorName != null ? operatorName : "SYSTEM");
        logEntry.setRemark(remark);
        logEntry.setBeforeStatus(wo.getStatus());
        logEntry.setAfterStatus(wo.getStatus());
        woLogRepository.save(logEntry);
    }
}
