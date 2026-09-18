package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyInspectionPlan;
import com.ibigou.blindbox.entity.PropertyInspectionRecord;
import com.ibigou.blindbox.entity.PropertyWoLog;
import com.ibigou.blindbox.entity.PropertyWorkorder;
import com.ibigou.blindbox.repository.PropertyInspectionPlanRepository;
import com.ibigou.blindbox.repository.PropertyInspectionRecordRepository;
import com.ibigou.blindbox.repository.PropertyWoLogRepository;
import com.ibigou.blindbox.repository.PropertyWorkorderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyInspectionService {

    private final PropertyInspectionPlanRepository planRepository;
    private final PropertyInspectionRecordRepository recordRepository;
    private final PropertyWorkorderRepository workorderRepository;
    private final PropertyWoLogRepository woLogRepository;

    @Transactional
    public PropertyInspectionPlan createPlan(Long companyId, String planName, String routeDesc,
                                             String checkpointIds, Integer frequency,
                                             String scheduleTime, String assigneeIds) {
        PropertyInspectionPlan plan = new PropertyInspectionPlan();
        plan.setCompanyId(companyId);
        plan.setPlanName(planName);
        plan.setRouteDesc(routeDesc);
        plan.setCheckpointIds(checkpointIds);
        plan.setFrequency(frequency);
        plan.setScheduleTime(scheduleTime);
        plan.setAssigneeIds(assigneeIds);
        plan.setStatus(1);
        return planRepository.save(plan);
    }

    @Transactional
    public void updatePlanStatus(Long planId, Integer status) {
        PropertyInspectionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BizException("巡检计划不存在"));
        plan.setStatus(status);
        planRepository.save(plan);
    }

    @Transactional
    public PropertyInspectionRecord checkIn(Long planId, Long inspectorId, String inspectorName,
                                            Long companyId, String checkpointId,
                                            BigDecimal lat, BigDecimal lng,
                                            Integer result, String issueDesc, String issueImages) {
        PropertyInspectionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BizException("巡检计划不存在"));
        if (plan.getStatus() != 1) {
            throw new BizException("巡检计划未启用");
        }

        PropertyInspectionRecord record = new PropertyInspectionRecord();
        record.setPlanId(planId);
        record.setInspectorId(inspectorId);
        record.setInspectorName(inspectorName);
        record.setCompanyId(companyId);
        record.setCheckpointId(checkpointId);
        record.setLocationLat(lat);
        record.setLocationLng(lng);
        record.setResult(result);
        record.setIssueDesc(issueDesc);
        record.setIssueImages(issueImages);
        record.setCheckDate(LocalDate.now());
        record.setCheckTime(LocalDateTime.now());
        record.setStatus(1);

        // 异常结果且有问题描述时，自动创建工单
        if (result == 2 && issueDesc != null && !issueDesc.isEmpty()) {
            PropertyWorkorder wo = new PropertyWorkorder();
            wo.setWoNo("WO" + System.currentTimeMillis());
            wo.setOwnerId(inspectorId);
            wo.setCompanyId(companyId);
            wo.setWoType(1);
            wo.setTitle("巡检异常-" + checkpointId);
            wo.setDescription(issueDesc);
            wo.setImages(issueImages);
            wo.setStatus(0);
            wo.setTimeoutCount(0);
            PropertyWorkorder savedWo = workorderRepository.save(wo);

            record.setWoId(savedWo.getWoId());

            // 记录工单创建日志
            PropertyWoLog logEntry = new PropertyWoLog();
            logEntry.setWoId(savedWo.getWoId());
            logEntry.setOperatorName(inspectorName);
            logEntry.setAction("CREATE");
            logEntry.setRemark("巡检异常自动创建工单");
            logEntry.setBeforeStatus(null);
            logEntry.setAfterStatus(0);
            woLogRepository.save(logEntry);

            log.info("Inspection abnormal workorder created: woId={}, checkpointId={}", savedWo.getWoId(), checkpointId);
        }

        return recordRepository.save(record);
    }

    public List<PropertyInspectionRecord> getRecordsByDateRange(Long companyId, LocalDate start, LocalDate end) {
        return recordRepository.findByCompanyIdAndCheckDateBetweenOrderByCheckTimeDesc(companyId, start, end);
    }

    public Map<String, Object> getInspectionStats(Long companyId, LocalDate date) {
        long totalPlans = planRepository.findAll().stream()
                .filter(p -> companyId.equals(p.getCompanyId()))
                .count();

        List<PropertyInspectionRecord> todayRecords = recordRepository
                .findByCompanyIdAndCheckDateBetweenOrderByCheckTimeDesc(companyId, date, date);

        long completedToday = todayRecords.size();
        long abnormalToday = todayRecords.stream()
                .filter(r -> r.getResult() != null && r.getResult() == 2)
                .count();

        double completionRate = totalPlans > 0 ? (double) completedToday / totalPlans * 100 : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPlans", totalPlans);
        stats.put("completedToday", completedToday);
        stats.put("abnormalToday", abnormalToday);
        stats.put("completionRate", completionRate);
        return stats;
    }
}
