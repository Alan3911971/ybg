package com.ibigou.blindbox.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 执行记录 / 审计（/api/property/audit）。
 *
 * 设计稿三条硬规矩，本类负责把它们落到代码里：
 *   ① 日志不可删 —— 只提供「撤回」（status=2），没有任何删除接口
 *   ② 三级授权 —— L1 只读直答 / L2 出草稿等人确认 / L3 只能你做，每条记录都带档位
 *   ③ 5 分钟撤回 —— undo_deadline 到期即不可撤，超时撤回直接拒
 *
 * 撤回是按动作真回滚（派单回退到待接单、拆单关掉子单、减免回退抵扣），不是只改状态字段。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/audit")
@RequiredArgsConstructor
public class PropertyAuditController {

    private final PropertyActionLogRepository logRepository;
    private final PropertyWorkorderRepository workorderRepository;
    private final PropertyBillRepository billRepository;
    private final PropertyBillReliefRepository reliefRepository;
    private final PropertyDeductLogRepository deductLogRepository;
    private final PropertyAdminRepository adminRepository;
    private final PropertyAdminSessionRepository sessionRepository;

    private PropertyAdmin validateAdmin(String token) {
        if (token == null || token.isBlank()) throw new BizException("未登录");
        PropertyAdminSession session = sessionRepository.findByTokenAndExpireTimeAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BizException("登录已过期，请重新登录"));
        return adminRepository.findById(session.getAdminId())
                .orElseThrow(() -> new BizException("管理员不存在"));
    }

    private Long resolveCompany(String token, Long companyId) {
        if (companyId != null) return companyId;
        return validateAdmin(token).getCompanyId();
    }

    /** 执行记录：默认近 24 小时，可按授权档 / 状态 / 动作筛 */
    @GetMapping("/logs")
    public Result<Map<String, Object>> logs(@RequestHeader("X-Property-Token") String token,
                                            @RequestParam(required = false) Long companyId,
                                            @RequestParam(required = false) String level,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) Integer hours,
                                            @RequestParam(required = false) Integer limit) {
        validateAdmin(token);
        Long cid = resolveCompany(token, companyId);
        LocalDateTime after = LocalDateTime.now().minusHours(hours == null ? 24 : hours);
        List<PropertyActionLog> all = logRepository.findByCompanyIdAndCreateTimeAfterOrderByLogIdDesc(cid, after);

        List<Map<String, Object>> rows = all.stream()
                .filter(l -> level == null || level.isBlank() || level.equalsIgnoreCase(l.getAuthLevel()))
                .filter(l -> status == null || status.equals(l.getStatus()))
                .filter(l -> action == null || action.isBlank() || action.equals(l.getAction()))
                .limit(limit == null ? 200 : limit)
                .map(this::rowOf)
                .collect(Collectors.toList());

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", rows);
        data.put("total", rows.size());
        // 统计按「今日」而不是「筛选后」——页面顶部的口径是当天实况
        List<PropertyActionLog> today = logRepository.findByCompanyIdAndCreateTimeAfterOrderByLogIdDesc(cid, todayStart);
        data.put("todayExecuted", today.stream().filter(l -> l.getStatus() != null && (l.getStatus() == 1 || l.getStatus() == 2)).count());
        data.put("todayWaiting", today.stream().filter(l -> l.getStatus() != null && l.getStatus() == 0).count());
        data.put("todayRejected", today.stream().filter(l -> l.getStatus() != null && l.getStatus() == 3).count());
        data.put("todayL3", today.stream().filter(l -> "L3".equals(l.getAuthLevel())).count());
        data.put("todayUndoable", today.stream().filter(this::canUndo).count());
        data.put("levelNote", "L1 只读直答（不改数据）· L2 出草稿等人确认 · L3 只能你做（动钱/动人权限/对外署名）");
        return Result.ok(data);
    }

    private Map<String, Object> rowOf(PropertyActionLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logId", l.getLogId());
        m.put("adminName", l.getAdminName() == null ? "系统" : l.getAdminName());
        m.put("action", l.getAction());
        m.put("actionLabel", l.getActionLabel());
        m.put("targetType", l.getTargetType());
        m.put("targetId", l.getTargetId());
        m.put("targetLabel", l.getTargetLabel());
        m.put("scopeDesc", l.getScopeDesc());
        m.put("authLevel", l.getAuthLevel());
        m.put("status", l.getStatus());
        m.put("statusName", statusName(l.getStatus()));
        m.put("note", l.getNote());
        m.put("batchNo", l.getBatchNo());
        m.put("beforeJson", l.getBeforeJson());
        m.put("afterJson", l.getAfterJson());
        m.put("createTime", l.getCreateTime() == null ? null : l.getCreateTime().toString().replace('T', ' ').substring(0, 19));
        m.put("undoDeadline", l.getUndoDeadline() == null ? null : l.getUndoDeadline().toString().replace('T', ' ').substring(0, 19));
        m.put("canUndo", canUndo(l));
        m.put("undoLeftSec", l.getUndoDeadline() == null ? 0
                : Math.max(0, java.time.Duration.between(LocalDateTime.now(), l.getUndoDeadline()).getSeconds()));
        return m;
    }

    private boolean canUndo(PropertyActionLog l) {
        return l.getStatus() != null && l.getStatus() == 1
                && l.getUndoDeadline() != null
                && LocalDateTime.now().isBefore(l.getUndoDeadline());
    }

    /** 5 分钟内撤回：按动作真回滚 */
    @Transactional
    @PostMapping("/logs/{id}/undo")
    public Result<Map<String, Object>> undo(@RequestHeader("X-Property-Token") String token,
                                            @PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyActionLog l = logRepository.findById(id).orElseThrow(() -> new BizException("记录不存在"));
        if (l.getStatus() == null || l.getStatus() != 1) throw new BizException("该记录当前不可撤回：" + statusName(l.getStatus()));
        if (l.getUndoDeadline() == null || LocalDateTime.now().isAfter(l.getUndoDeadline())) {
            throw new BizException("已超过 5 分钟撤回窗口，需要走反向操作（新记录一条，不删原记录）");
        }
        String reason = body == null || body.get("reason") == null ? "发起人撤回" : String.valueOf(body.get("reason"));

        String detail = rollback(l);

        l.setStatus(2);
        l.setNote((l.getNote() == null ? "" : l.getNote() + " | ") + "撤回：" + reason);
        logRepository.save(l);

        // 撤回本身也是一条记录（日志不可删，撤回留痕）
        PropertyActionLog undoLog = new PropertyActionLog();
        undoLog.setCompanyId(l.getCompanyId());
        undoLog.setAdminId(admin.getAdminId());
        undoLog.setAdminName(admin.getAdminName());
        undoLog.setAction("undo");
        undoLog.setActionLabel("撤回动作");
        undoLog.setTargetType(l.getTargetType());
        undoLog.setTargetId(l.getTargetId());
        undoLog.setTargetLabel(l.getTargetLabel());
        undoLog.setScopeDesc("撤回 #" + l.getLogId() + "「" + l.getActionLabel() + "」· " + detail);
        undoLog.setAuthLevel(l.getAuthLevel());
        undoLog.setStatus(1);
        undoLog.setNote(reason);
        logRepository.save(undoLog);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logId", l.getLogId());
        data.put("status", l.getStatus());
        data.put("statusName", statusName(l.getStatus()));
        data.put("detail", detail);
        data.put("undoLogId", undoLog.getLogId());
        return Result.ok(data);
    }

    /** 按动作回滚，返回一句给人看的结果说明 */
    private String rollback(PropertyActionLog l) {
        String act = l.getAction() == null ? "" : l.getAction();
        switch (act) {
            case "dispatch": {
                PropertyWorkorder wo = workorderRepository.findById(l.getTargetId()).orElse(null);
                if (wo == null) return "工单已不存在，未做变更";
                JSONObject before = parse(l.getBeforeJson());
                wo.setStatus(before.getInteger("status"));
                wo.setAssigneeName(before.getString("assigneeName"));
                if (before.getInteger("status") != null && before.getInteger("status") == 0) wo.setAssigneeId(null);
                workorderRepository.save(wo);
                return "工单 " + wo.getWoNo() + " 已回退到「待接单」，负责人清空";
            }
            case "split": {
                PropertyWorkorder origin = workorderRepository.findById(l.getTargetId()).orElse(null);
                int closed = 0;
                try {
                    List<JSONObject> subs = JSON.parseArray(l.getAfterJson(), JSONObject.class);
                    if (subs != null) {
                        for (JSONObject s : subs) {
                            Long subId = s.getLong("woId");
                            if (subId == null) continue;
                            PropertyWorkorder sub = workorderRepository.findById(subId).orElse(null);
                            if (sub != null) { sub.setStatus(4); workorderRepository.save(sub); closed++; }
                        }
                    }
                } catch (Exception e) {
                    log.warn("拆单撤回解析子单失败 logId={}", l.getLogId(), e);
                }
                if (origin != null) { origin.setStatus(0); workorderRepository.save(origin); }
                return "已关闭 " + closed + " 条子单，原单 " + (origin == null ? "--" : origin.getWoNo()) + " 回到待接单";
            }
            case "relief-approve": {
                JSONObject before = parse(l.getBeforeJson());
                PropertyBill bill = billRepository.findById(l.getTargetId()).orElse(null);
                if (bill == null) return "账单已不存在，未做变更";
                BigDecimal old = before.getBigDecimal("deducted");
                bill.setDeducted(old == null ? BigDecimal.ZERO : old);
                billRepository.save(bill);
                JSONObject after = parse(l.getAfterJson());
                Long dlId = after.getLong("deductLogId");
                if (dlId != null) deductLogRepository.deleteById(dlId);
                // 对应申请退回待批，避免"批了又撤"后状态卡在已通过
                reliefRepository.findByCompanyIdAndBillId(l.getCompanyId(), l.getTargetId()).stream()
                        .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                        .findFirst()
                        .ifPresent(r -> { r.setStatus(0); r.setApproverId(null); r.setApproverName(null); reliefRepository.save(r); });
                return "账单 " + bill.getBillNo() + " 抵扣已回退至 ￥" + bill.getDeducted() + "，减免申请退回待批";
            }
            case "relief-apply": {
                JSONObject after = parse(l.getAfterJson());
                Long rid = after.getLong("reliefId");
                if (rid == null) return "未找到对应申请";
                PropertyBillRelief r = reliefRepository.findById(rid).orElse(null);
                if (r == null) return "申请已不存在";
                r.setStatus(2);
                r.setApproveNote("申请人撤回");
                reliefRepository.save(r);
                return "减免申请 #" + rid + " 已撤回（账目从未改动）";
            }
            default:
                return "该动作无可回滚项，仅标记为已撤回";
        }
    }

    private JSONObject parse(String s) {
        if (s == null || s.isBlank()) return new JSONObject();
        try {
            JSONObject o = JSON.parseObject(s);
            return o == null ? new JSONObject() : o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 审计说明：给业委会看的一页话术 + 数字 */
    @GetMapping("/statement")
    public Result<Map<String, Object>> statement(@RequestHeader("X-Property-Token") String token,
                                                 @RequestParam(required = false) Long companyId,
                                                 @RequestParam(required = false) Integer days) {
        validateAdmin(token);
        Long cid = resolveCompany(token, companyId);
        int d = days == null ? 30 : days;
        LocalDateTime after = LocalDateTime.now().minusDays(d);
        List<PropertyActionLog> all = logRepository.findByCompanyIdAndCreateTimeAfterOrderByLogIdDesc(cid, after);

        Map<String, Long> byLevel = new LinkedHashMap<>();
        for (String lv : new String[]{"L1", "L2", "L3"}) {
            byLevel.put(lv, all.stream().filter(x -> lv.equals(x.getAuthLevel())).count());
        }
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (int s : new int[]{0, 1, 2, 3}) {
            final int ss = s;
            byStatus.put(statusName(s), all.stream().filter(x -> x.getStatus() != null && x.getStatus() == ss).count());
        }
        Map<String, Long> byAction = new LinkedHashMap<>();
        for (PropertyActionLog x : all) {
            byAction.merge(x.getActionLabel() == null ? x.getAction() : x.getActionLabel(), 1L, Long::sum);
        }
        List<Map<String, Object>> top = byAction.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(8)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("label", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", d);
        data.put("total", all.size());
        data.put("byLevel", byLevel);
        data.put("byStatus", byStatus);
        data.put("topActions", top);
        data.put("statement", "近 " + d + " 天共记录 " + all.size() + " 条动作，其中 L3（动钱/动人权限/对外署名）"
                + byLevel.getOrDefault("L3", 0L) + " 条全部有申请人、审批人与可撤回窗口；"
                + "撤回 " + byStatus.getOrDefault("已撤回", 0L) + " 条，驳回 " + byStatus.getOrDefault("已驳回", 0L)
                + " 条。日志不可删除，任何修改都会新增一条记录。");
        return Result.ok(data);
    }

    /** 单条详情（页面点「详情」看前后快照） */
    @GetMapping("/logs/{id}")
    public Result<Map<String, Object>> detail(@RequestHeader("X-Property-Token") String token,
                                              @PathVariable Long id) {
        validateAdmin(token);
        PropertyActionLog l = logRepository.findById(id).orElseThrow(() -> new BizException("记录不存在"));
        return Result.ok(rowOf(l));
    }

    private String statusName(Integer s) {
        if (s == null) return "未知";
        switch (s) {
            case 0: return "待执行";
            case 1: return "已执行";
            case 2: return "已撤回";
            case 3: return "已驳回";
            default: return "状态" + s;
        }
    }
}
