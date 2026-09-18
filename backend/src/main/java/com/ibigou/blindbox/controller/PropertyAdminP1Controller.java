package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import com.ibigou.blindbox.service.PropertyInspectionService;
import com.ibigou.blindbox.service.PropertyTempParkingService;
import com.ibigou.blindbox.service.PropertyValueServiceOrderService;
import com.ibigou.blindbox.service.PropertyWorkorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 物业管理后台 P1 扩展 API。
 * 鉴权：X-Property-Token（本控制器内部校验）。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/admin/p1")
@RequiredArgsConstructor
public class PropertyAdminP1Controller {

    private final PropertyWorkorderService workorderService;
    private final PropertyInspectionService inspectionService;
    private final PropertyValueServiceOrderService vsOrderService;
    private final PropertyTempParkingService tempParkingService;
    private final PropertyFaceInfoRepository faceInfoRepository;
    private final PropertyVisitorVehicleRepository visitorVehicleRepository;
    private final PropertyWorkorderRepository workorderRepository;
    private final PropertyInspectionPlanRepository planRepository;
    private final PropertyInspectionRecordRepository recordRepository;
    private final PropertyVsOrderRepository vsOrderRepository;
    private final PropertyTempParkingPaymentRepository tempParkingPaymentRepository;
    private final PropertyAdminSessionRepository sessionRepository;
    private final PropertyAdminRepository adminRepository;
    private final PropertyWoLogRepository woLogRepository;
    private final PropertySplitRecordRepository splitRecordRepository;
    private final MerchantPropertyBindingRepository bindingRepository;

    // ---------------- Token 校验 ----------------

    private PropertyAdmin validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException("未登录");
        }
        PropertyAdminSession session = sessionRepository.findByTokenAndExpireTimeAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BizException("登录已过期，请重新登录"));
        return adminRepository.findById(session.getAdminId())
                .orElseThrow(() -> new BizException("管理员不存在"));
    }

    // ==================== 工单管理 ====================

    /**
     * 1. 工单列表（可按状态、类型过滤）
     */
    @GetMapping("/workorders")
    public Result<List<PropertyWorkorder>> workorders(@RequestHeader("X-Property-Token") String token,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(required = false) Integer type) {
        validateToken(token);
        List<PropertyWorkorder> all = workorderRepository.findAll();
        List<PropertyWorkorder> filtered = all.stream()
                .filter(wo -> status == null || status.equals(wo.getStatus()))
                .filter(wo -> type == null || type.equals(wo.getWoType()))
                .collect(Collectors.toList());
        return Result.ok(filtered);
    }

    /**
     * 2. 指派工单
     */
    @PostMapping("/workorders/{id}/assign")
    public Result<Void> assignWorkorder(@RequestHeader("X-Property-Token") String token,
                                        @PathVariable Long id,
                                        @RequestParam Long assigneeId,
                                        @RequestParam String assigneeName) {
        PropertyAdmin admin = validateToken(token);
        workorderService.assignWorkorder(id, assigneeId, assigneeName, admin.getAdminName());
        return Result.ok();
    }

    /**
     * 3. 转派工单
     */
    @PostMapping("/workorders/{id}/transfer")
    public Result<Void> transferWorkorder(@RequestHeader("X-Property-Token") String token,
                                          @PathVariable Long id,
                                          @RequestParam Long newAssigneeId,
                                          @RequestParam String newAssigneeName,
                                          @RequestParam String reason) {
        PropertyAdmin admin = validateToken(token);
        workorderService.transferWorkorder(id, newAssigneeId, newAssigneeName, reason, admin.getAdminName());
        return Result.ok();
    }

    /**
     * 4. 完成工单
     */
    @PostMapping("/workorders/{id}/complete")
    public Result<Void> completeWorkorder(@RequestHeader("X-Property-Token") String token,
                                          @PathVariable Long id) {
        PropertyAdmin admin = validateToken(token);
        workorderService.completeWorkorder(id, admin.getAdminName());
        return Result.ok();
    }

    /**
     * 5. 关闭工单
     */
    @PostMapping("/workorders/{id}/close")
    public Result<Void> closeWorkorder(@RequestHeader("X-Property-Token") String token,
                                       @PathVariable Long id) {
        PropertyAdmin admin = validateToken(token);
        workorderService.closeWorkorder(id, admin.getAdminName());
        return Result.ok();
    }

    /**
     * 6. 工单操作日志
     */
    @GetMapping("/workorders/{id}/logs")
    public Result<List<PropertyWoLog>> workorderLogs(@RequestHeader("X-Property-Token") String token,
                                                     @PathVariable Long id) {
        validateToken(token);
        return Result.ok(woLogRepository.findByWoIdOrderByCreateTimeAsc(id));
    }

    // ==================== 巡检管理 ====================

    /**
     * 7. 巡检计划列表
     */
    @GetMapping("/inspection/plans")
    public Result<List<PropertyInspectionPlan>> inspectionPlans(@RequestHeader("X-Property-Token") String token,
                                                                @RequestParam Long companyId) {
        validateToken(token);
        return Result.ok(planRepository.findByCompanyIdAndStatus(companyId, 1));
    }

    /**
     * 8. 创建巡检计划
     */
    @PostMapping("/inspection/plans")
    public Result<PropertyInspectionPlan> createInspectionPlan(@RequestHeader("X-Property-Token") String token,
                                                               @RequestParam Long companyId,
                                                               @RequestParam String planName,
                                                               @RequestParam String routeDesc,
                                                               @RequestParam String checkpointIds,
                                                               @RequestParam Integer frequency,
                                                               @RequestParam String scheduleTime,
                                                               @RequestParam String assigneeIds) {
        validateToken(token);
        PropertyInspectionPlan plan = inspectionService.createPlan(companyId, planName, routeDesc,
                checkpointIds, frequency, scheduleTime, assigneeIds);
        return Result.ok(plan);
    }

    /**
     * 9. 更新巡检计划状态
     */
    @PutMapping("/inspection/plans/{id}/status")
    public Result<Void> updatePlanStatus(@RequestHeader("X-Property-Token") String token,
                                         @PathVariable Long id,
                                         @RequestParam Integer status) {
        validateToken(token);
        inspectionService.updatePlanStatus(id, status);
        return Result.ok();
    }

    /**
     * 10. 巡检记录列表（按日期范围）
     */
    @GetMapping("/inspection/records")
    public Result<List<PropertyInspectionRecord>> inspectionRecords(@RequestHeader("X-Property-Token") String token,
                                                                    @RequestParam Long companyId,
                                                                    @RequestParam String startDate,
                                                                    @RequestParam String endDate) {
        validateToken(token);
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return Result.ok(inspectionService.getRecordsByDateRange(companyId, start, end));
    }

    /**
     * 11. 巡检统计
     */
    @GetMapping("/inspection/stats")
    public Result<Map<String, Object>> inspectionStats(@RequestHeader("X-Property-Token") String token,
                                                       @RequestParam Long companyId) {
        validateToken(token);
        return Result.ok(inspectionService.getInspectionStats(companyId, LocalDate.now()));
    }

    // ==================== 增值服务订单 ====================

    /**
     * 12. 增值服务订单列表
     */
    @GetMapping("/vs-orders")
    public Result<List<PropertyVsOrder>> vsOrders(@RequestHeader("X-Property-Token") String token,
                                                  @RequestParam(required = false) Long companyId,
                                                  @RequestParam(required = false) Integer status) {
        validateToken(token);
        List<PropertyVsOrder> all = vsOrderRepository.findAll();
        List<PropertyVsOrder> filtered = all.stream()
                .filter(o -> companyId == null || companyId.equals(o.getCompanyId()))
                .filter(o -> status == null || status.equals(o.getStatus()))
                .collect(Collectors.toList());
        return Result.ok(filtered);
    }

    /**
     * 13. 完成增值服务订单
     */
    @PostMapping("/vs-orders/{orderNo}/complete")
    public Result<Void> completeVsOrder(@RequestHeader("X-Property-Token") String token,
                                        @PathVariable String orderNo) {
        PropertyAdmin admin = validateToken(token);
        vsOrderService.completeOrder(orderNo, admin.getAdminName());
        return Result.ok();
    }

    /**
     * 14. 退款增值服务订单
     */
    @PostMapping("/vs-orders/{orderNo}/refund")
    public Result<Void> refundVsOrder(@RequestHeader("X-Property-Token") String token,
                                      @PathVariable String orderNo) {
        PropertyAdmin admin = validateToken(token);
        vsOrderService.refundOrder(orderNo, admin.getAdminName());
        return Result.ok();
    }

    // ==================== 人脸审核 ====================

    /**
     * 15. 人脸审核列表
     */
    @GetMapping("/face-audits")
    public Result<List<PropertyFaceInfo>> faceAudits(@RequestHeader("X-Property-Token") String token,
                                                     @RequestParam(required = false) Integer status) {
        validateToken(token);
        if (status != null) {
            return Result.ok(faceInfoRepository.findByAuditStatus(status));
        }
        return Result.ok(faceInfoRepository.findAll());
    }

    /**
     * 16. 通过人脸审核
     */
    @PutMapping("/face-audits/{id}/approve")
    public Result<Void> approveFaceAudit(@RequestHeader("X-Property-Token") String token,
                                         @PathVariable Long id) {
        PropertyAdmin admin = validateToken(token);
        PropertyFaceInfo faceInfo = faceInfoRepository.findById(id)
                .orElseThrow(() -> new BizException("人脸信息不存在"));
        faceInfo.setAuditStatus(1);
        faceInfo.setAuditorId(admin.getAdminId());
        faceInfo.setAuditTime(LocalDateTime.now());
        faceInfoRepository.save(faceInfo);
        log.info("Face audit approved: faceId={}, auditor={}", id, admin.getAdminId());
        return Result.ok();
    }

    /**
     * 17. 驳回人脸审核
     */
    @PutMapping("/face-audits/{id}/reject")
    public Result<Void> rejectFaceAudit(@RequestHeader("X-Property-Token") String token,
                                        @PathVariable Long id,
                                        @RequestParam String remark) {
        PropertyAdmin admin = validateToken(token);
        PropertyFaceInfo faceInfo = faceInfoRepository.findById(id)
                .orElseThrow(() -> new BizException("人脸信息不存在"));
        faceInfo.setAuditStatus(2);
        faceInfo.setAuditRemark(remark);
        faceInfo.setAuditorId(admin.getAdminId());
        faceInfo.setAuditTime(LocalDateTime.now());
        faceInfoRepository.save(faceInfo);
        log.info("Face audit rejected: faceId={}, auditor={}", id, admin.getAdminId());
        return Result.ok();
    }

    // ==================== 访客车辆 ====================

    /**
     * 18. 访客车辆列表（活跃）
     */
    @GetMapping("/visitor-vehicles")
    public Result<List<PropertyVisitorVehicle>> visitorVehicles(@RequestHeader("X-Property-Token") String token,
                                                                @RequestParam(required = false) Long communityId) {
        validateToken(token);
        if (communityId != null) {
            return Result.ok(visitorVehicleRepository.findByCommunityIdAndStatusAndExpireTimeAfter(
                    communityId, 1, LocalDateTime.now()));
        }
        // 无 communityId 时返回所有活跃的访客车辆（内存过滤）
        List<PropertyVisitorVehicle> all = visitorVehicleRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        List<PropertyVisitorVehicle> active = all.stream()
                .filter(v -> v.getStatus() != null && v.getStatus() == 1)
                .filter(v -> v.getExpireTime() != null && v.getExpireTime().isAfter(now))
                .collect(Collectors.toList());
        return Result.ok(active);
    }

    // ==================== 临时车缴费 ====================

    /**
     * 19. 临时停车缴费记录
     */
    @GetMapping("/temp-payments")
    public Result<List<PropertyTempParkingPayment>> tempPayments(@RequestHeader("X-Property-Token") String token,
                                                                 @RequestParam(required = false) Long communityId,
                                                                 @RequestParam(required = false) Long community_id,
                                                                 @RequestParam(required = false) String startDate,
                                                                 @RequestParam(required = false) String start_date,
                                                                 @RequestParam(required = false) String endDate,
                                                                 @RequestParam(required = false) String end_date) {
        validateToken(token);
        if (communityId == null) {
            communityId = community_id;
        }
        if ((startDate == null || startDate.isBlank())) {
            startDate = start_date;
        }
        if ((endDate == null || endDate.isBlank())) {
            endDate = end_date;
        }
        LocalDateTime startTime = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDate.now().plusDays(1).atStartOfDay();
        if (startDate != null && !startDate.isBlank()) {
            startTime = LocalDate.parse(startDate).atStartOfDay();
        }
        if (endDate != null && !endDate.isBlank()) {
            endTime = LocalDate.parse(endDate).atTime(LocalTime.MAX);
        }
        if (communityId != null) {
            return Result.ok(tempParkingPaymentRepository.findByCommunityIdAndPayTimeBetweenOrderByPayTimeDesc(
                    communityId, startTime, endTime));
        }
        return Result.ok(tempParkingPaymentRepository.findByPayTimeBetweenOrderByPayTimeDesc(startTime, endTime));
    }

    // ==================== 双模式分账管理 ====================

    /**
     * 查询分账记录（支持按 splitMode 筛选）。
     * splitMode: null=全部, 1=商家→物业, 2=平台→物业
     */
    @GetMapping("/split-records")
    public Result<List<Map<String, Object>>> splitRecords(@RequestHeader("X-Property-Token") String token,
                                                          @RequestParam(required = false) Long companyId,
                                                          @RequestParam(required = false) Integer splitMode) {
        validateToken(token);
        List<PropertySplitRecord> records = splitRecordRepository.findAll();
        return Result.ok(records.stream()
                .filter(r -> companyId == null || companyId.equals(r.getCompanyId()))
                .filter(r -> splitMode == null || splitMode.equals(r.getSplitMode()))
                .map(r -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("splitId", r.getSplitId());
                    m.put("orderNo", r.getOrderNo());
                    m.put("merchantNo", r.getMerchantNo());
                    m.put("companyId", r.getCompanyId());
                    m.put("splitMode", r.getSplitMode());
                    m.put("splitModeLabel", r.getSplitMode() != null && r.getSplitMode() == 2 ? "平台→物业" : "商家→物业");
                    m.put("orderAmount", r.getOrderAmount());
                    m.put("splitAmount", r.getSplitAmount());
                    m.put("splitStatus", r.getSplitStatus());
                    m.put("createTime", r.getCreateTime());
                    return m;
                })
                .collect(Collectors.toList()));
    }

    /**
     * 查询商家-物业绑定列表（含双模式标识）。
     */
    @GetMapping("/merchant-bindings")
    public Result<List<MerchantPropertyBinding>> merchantBindings(@RequestHeader("X-Property-Token") String token,
                                                                  @RequestParam(required = false) Long companyId,
                                                                  @RequestParam(required = false) Integer splitMode) {
        validateToken(token);
        List<MerchantPropertyBinding> bindings;
        if (companyId != null && splitMode != null) {
            bindings = bindingRepository.findByCompanyIdAndSplitModeAndStatus(companyId, splitMode, 1);
        } else if (companyId != null) {
            bindings = bindingRepository.findByCompanyIdAndStatus(companyId, 1);
        } else {
            bindings = bindingRepository.findAll();
        }
        return Result.ok(bindings);
    }
}
