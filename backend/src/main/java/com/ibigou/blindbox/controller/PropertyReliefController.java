package com.ibigou.blindbox.controller;

import com.alibaba.fastjson.JSON;
import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 单户账单减免（/api/property/relief）。
 *
 * 设计稿口径（写在明处，不靠提示词）：
 *   - 减免属于 L3「只能你做」：AI 只出证据包，不改金额
 *   - 金额超角色额度 → 只能出证据包（status=3），账目一分不动
 *   - 额度内 → 进待批（status=0），必须第二人审批（审批人 ≠ 申请人）
 *   - 审批通过才写账：bill.deducted += 减免额，并落 property_deduct_log（source_type=9）
 *   - 每次动作进 property_action_log，5 分钟内可撤回；日志不可删
 */
@Slf4j
@RestController
@RequestMapping("/api/property/relief")
@RequiredArgsConstructor
public class PropertyReliefController {

    private final PropertyBillRepository billRepository;
    private final PropertyBillReliefRepository reliefRepository;
    private final PropertyDeductLogRepository deductLogRepository;
    private final PropertyRoomRepository roomRepository;
    private final PropertyBuildingRepository buildingRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyAdminRepository adminRepository;
    private final PropertyAdminSessionRepository sessionRepository;
    private final PropertyActionLogRepository actionLogRepository;

    /** 减免额的来源：9 = 物业减免（不与消费抵扣 1/2/3 混在一起，审计才分得清） */
    private static final int SOURCE_RELIEF = 9;

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

    /** 角色单笔额度（写死在引擎里；改额度要改代码 + 执行记录留痕，不能后台随手调） */
    private BigDecimal quotaOf(PropertyAdmin admin) {
        Integer role = admin.getRoleType();
        if (role == null) return new BigDecimal("100");
        switch (role) {
            case 1: return new BigDecimal("1000"); // 超级管理员 / 项目经理
            case 2: return new BigDecimal("500");  // 主管
            case 3: return new BigDecimal("100");  // 客服
            default: return new BigDecimal("100");
        }
    }

    private String roleName(Integer role) {
        if (role == null) return "未分级";
        switch (role) {
            case 1: return "超级管理员";
            case 2: return "主管";
            case 3: return "客服";
            default: return "角色" + role;
        }
    }

    private String roomLabel(Long roomId) {
        if (roomId == null) return null;
        PropertyRoom room = roomRepository.findById(roomId).orElse(null);
        if (room == null) return null;
        String b = buildingRepository.findById(room.getBuildingId()).map(PropertyBuilding::getBuildingName).orElse("");
        return (b.isEmpty() ? "" : b + " ") + (room.getUnitNo() == null ? "" : room.getUnitNo() + "-")
                + (room.getFloorNo() == null ? "" : room.getFloorNo()) + "-"
                + (room.getRoomNo() == null ? "" : room.getRoomNo());
    }

    private BigDecimal unpaidOf(PropertyBill b) {
        BigDecimal amt = b.getAmount() == null ? BigDecimal.ZERO : b.getAmount();
        BigDecimal ded = b.getDeducted() == null ? BigDecimal.ZERO : b.getDeducted();
        BigDecimal paid = b.getPaid() == null ? BigDecimal.ZERO : b.getPaid();
        BigDecimal v = amt.subtract(ded).subtract(paid);
        return v.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : v;
    }

    // =====================================================================
    // 候选：哪些户真该考虑减免（不是所有欠费户都减免）
    // =====================================================================
    @GetMapping("/candidates")
    public Result<Map<String, Object>> candidates(@RequestHeader("X-Property-Token") String token,
                                                  @RequestParam(required = false) Long companyId,
                                                  @RequestParam(required = false) Integer limit) {
        PropertyAdmin admin = validateAdmin(token);
        Long cid = resolveCompany(token, companyId);
        BigDecimal quota = quotaOf(admin);
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> rows = new ArrayList<>();
        int overQuota = 0, inQuota = 0;
        BigDecimal sumUnpaid = BigDecimal.ZERO;

        List<PropertyBill> bills = billRepository.findByCompanyIdOrderByDueDateAsc(cid);
        List<PropertyBill> pending = bills.stream()
                .filter(b -> b.getStatus() != null && b.getStatus() != 1)
                .filter(b -> unpaidOf(b).compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        for (PropertyBill b : pending) {
            BigDecimal unpaid = unpaidOf(b);
            long overdueDays = b.getDueDate() == null ? 0 : Duration.between(b.getDueDate().atStartOfDay(), today.atStartOfDay()).toDays();
            if (overdueDays <= 0) continue;

            // 分级：逾期越久、金额越小，越可能是「有争议」而不是「没钱」——这两类处理方式不同
            String grade;
            String gradeNote;
            if (overdueDays >= 90) { grade = "A"; gradeNote = "逾期 ≥90 天，通常已有争议或空置，需先对账再谈减免"; }
            else if (overdueDays >= 30) { grade = "B"; gradeNote = "逾期 30-90 天，可走减免换取一次结清"; }
            else { grade = "C"; gradeNote = "逾期 <30 天，优先催缴，不建议减免"; }

            String ownerName = ownerRepository.findById(b.getOwnerId()).map(PropertyOwner::getOwnerName).orElse("业主" + b.getOwnerId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("billId", b.getBillId());
            r.put("billNo", b.getBillNo());
            r.put("billPeriod", b.getBillPeriod());
            r.put("ownerId", b.getOwnerId());
            r.put("ownerName", ownerName);
            r.put("roomLabel", roomLabel(b.getRoomId()));
            r.put("amount", b.getAmount());
            r.put("deducted", b.getDeducted());
            r.put("paid", b.getPaid());
            r.put("unpaid", unpaid);
            r.put("dueDate", b.getDueDate() == null ? null : b.getDueDate().toString());
            r.put("overdueDays", overdueDays);
            r.put("grade", grade);
            r.put("gradeNote", gradeNote);
            // 建议减免额：A 类只减滞纳金量级（按未结清 3%封顶），B 类按 5%，C 类 0
            BigDecimal suggest = unpaid.multiply(new BigDecimal("A".equals(grade) ? "0.03" : "B".equals(grade) ? "0.05" : "0"))
                    .setScale(2, RoundingMode.HALF_UP);
            r.put("suggestAmount", suggest);
            r.put("withinQuota", suggest.compareTo(quota) <= 0);
            r.put("quota", quota);
            if (suggest.compareTo(quota) <= 0) inQuota++; else overQuota++;
            sumUnpaid = sumUnpaid.add(unpaid);
            rows.add(r);
        }
        rows.sort((a, b) -> ((Long) b.get("overdueDays")).compareTo((Long) a.get("overdueDays")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", limit == null ? rows : rows.stream().limit(limit).collect(Collectors.toList()));
        data.put("total", rows.size());
        data.put("sumUnpaid", sumUnpaid);
        data.put("inQuota", inQuota);
        data.put("overQuota", overQuota);
        data.put("myRole", roleName(admin.getRoleType()));
        data.put("myQuota", quota);
        data.put("ruleNote", "减免恒为 L3：AI 只出证据包不改金额；超角色额度只能出证据包；额度内也需第二人审批。");
        return Result.ok(data);
    }

    /** 证据包：账单 + 抵扣流水 + 近 12 期历史 + 同户型对比 + 历史减免次数 */
    @GetMapping("/evidence")
    public Result<Map<String, Object>> evidence(@RequestHeader("X-Property-Token") String token,
                                                @RequestParam Long billId) {
        validateAdmin(token);
        PropertyBill bill = billRepository.findById(billId).orElseThrow(() -> new BizException("账单不存在"));
        return Result.ok(buildEvidence(bill));
    }

    private Map<String, Object> buildEvidence(PropertyBill bill) {
        Long billId = bill.getBillId();
        Map<String, Object> billPart = new LinkedHashMap<>();
        billPart.put("billNo", bill.getBillNo());
        billPart.put("billPeriod", bill.getBillPeriod());
        billPart.put("amount", bill.getAmount());
        billPart.put("deducted", bill.getDeducted());
        billPart.put("paid", bill.getPaid());
        billPart.put("unpaid", unpaidOf(bill));
        billPart.put("dueDate", bill.getDueDate() == null ? null : bill.getDueDate().toString());
        billPart.put("status", bill.getStatus());

        List<Map<String, Object>> deducts = deductLogRepository.findAll().stream()
                .filter(d -> billId.equals(d.getBillId()))
                .limit(20)
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("deductAmount", d.getDeductAmount());
                    m.put("sourceType", d.getSourceType());
                    m.put("sourceName", d.getSourceType() != null && d.getSourceType() == SOURCE_RELIEF ? "物业减免" : "消费抵扣");
                    m.put("createTime", d.getCreateTime() == null ? null : d.getCreateTime().toString().replace('T', ' ').substring(0, 16));
                    return m;
                }).collect(Collectors.toList());

        // 该业主近 12 期缴费历史
        List<PropertyBill> hist = billRepository.findByRoomIdOrderByBillPeriodAsc(bill.getRoomId());
        List<Map<String, Object>> history = hist.stream().limit(12).map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("billPeriod", h.getBillPeriod());
            m.put("amount", h.getAmount());
            m.put("paid", h.getPaid());
            m.put("deducted", h.getDeducted());
            m.put("status", h.getStatus());
            m.put("statusName", h.getStatus() != null && h.getStatus() == 1 ? "已结清" : "未结清");
            return m;
        }).collect(Collectors.toList());

        // 同户型（同楼栋同 roomType）对比：欠费户数占比
        PropertyRoom room = bill.getRoomId() == null ? null : roomRepository.findById(bill.getRoomId()).orElse(null);
        Map<String, Object> compare = new LinkedHashMap<>();
        if (room != null) {
            List<PropertyRoom> same = roomRepository.findByBuildingId(room.getBuildingId()).stream()
                    .filter(r -> Objects.equals(r.getRoomType(), room.getRoomType()))
                    .collect(Collectors.toList());
            Set<Long> sameIds = same.stream().map(PropertyRoom::getRoomId).collect(Collectors.toSet());
            List<PropertyBill> sameBills = billRepository.findByCompanyIdAndBillPeriod(bill.getCompanyId(), bill.getBillPeriod()).stream()
                    .filter(b -> sameIds.contains(b.getRoomId()))
                    .collect(Collectors.toList());
            long unpaidCnt = sameBills.stream().filter(b -> unpaidOf(b).compareTo(BigDecimal.ZERO) > 0).count();
            BigDecimal avg = sameBills.isEmpty() ? BigDecimal.ZERO
                    : sameBills.stream().map(PropertyBill::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(sameBills.size()), 2, RoundingMode.HALF_UP);
            compare.put("roomType", room.getRoomType());
            compare.put("sameCount", sameBills.size());
            compare.put("sameUnpaidCount", unpaidCnt);
            compare.put("sameAvgAmount", avg);
            compare.put("thisAmount", bill.getAmount());
            compare.put("note", sameBills.isEmpty() ? "本户型本期无账单可比"
                    : "同户型 " + sameBills.size() + " 户中 " + unpaidCnt + " 户未结清，户均应收 ￥" + avg);
        } else {
            compare.put("note", "该账单未关联房号，无法做同户型对比");
        }

        long reliefTimes = reliefRepository.findByCompanyIdAndOwnerIdOrderByReliefIdDesc(bill.getCompanyId(), bill.getOwnerId()).size();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bill", billPart);
        data.put("deducts", deducts);
        data.put("history", history);
        data.put("compare", compare);
        data.put("reliefTimes", reliefTimes);
        data.put("generatedAt", LocalDateTime.now().toString().replace('T', ' ').substring(0, 19));
        data.put("disclaimer", "本证据包只陈述数据，不含结论。减免与否由人决定，AI 不代替签字。");
        return data;
    }

    // =====================================================================
    // 申请 / 审批 / 驳回
    // =====================================================================
    @Transactional
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestHeader("X-Property-Token") String token,
                                             @RequestBody Map<String, Object> body) {
        PropertyAdmin admin = validateAdmin(token);
        Long billId = body.get("billId") == null ? null : Long.valueOf(String.valueOf(body.get("billId")));
        if (billId == null) throw new BizException("请选择账单");
        PropertyBill bill = billRepository.findById(billId).orElseThrow(() -> new BizException("账单不存在"));

        BigDecimal amount = body.get("amount") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(body.get("amount")));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("减免金额须大于 0");
        BigDecimal unpaid = unpaidOf(bill);
        if (amount.compareTo(unpaid) > 0) throw new BizException("减免金额不能超过未结清金额 ￥" + unpaid);

        Integer type = body.get("reliefType") == null ? 1 : Integer.valueOf(String.valueOf(body.get("reliefType")));
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
        if (reason.isBlank()) throw new BizException("请填写减免理由（这条理由会进审计）");

        BigDecimal quota = quotaOf(admin);
        Map<String, Object> ev = buildEvidence(bill);

        PropertyBillRelief r = new PropertyBillRelief();
        r.setCompanyId(bill.getCompanyId());
        r.setBillId(billId);
        r.setOwnerId(bill.getOwnerId());
        r.setRoomId(bill.getRoomId());
        r.setReliefType(type);
        r.setApplyAmount(amount);
        r.setQuotaAmount(quota);
        r.setAuthLevel("L3");
        r.setApplicantId(admin.getAdminId());
        r.setApplicantName(admin.getAdminName());
        r.setReason(reason);
        r.setEvidenceJson(JSON.toJSONString(ev));

        boolean over = amount.compareTo(quota) > 0;
        r.setStatus(over ? 3 : 0);
        reliefRepository.save(r);

        PropertyActionLog log = new PropertyActionLog();
        log.setCompanyId(bill.getCompanyId());
        log.setAdminId(admin.getAdminId());
        log.setAdminName(admin.getAdminName());
        log.setAction("relief-apply");
        log.setActionLabel(over ? "减免超额度 · 只出证据包" : "提交减免申请");
        log.setTargetType("bill");
        log.setTargetId(billId);
        log.setTargetLabel(bill.getBillNo() + " · " + (roomLabel(bill.getRoomId()) == null ? "房号待补" : roomLabel(bill.getRoomId())));
        log.setScopeDesc("账单 ￥" + bill.getAmount() + " · 未结清 ￥" + unpaid + " · 申请减免 ￥" + amount
                + " · 我的额度 ￥" + quota + (over ? " · 超限，账目未改动" : " · 待第二人审批"));
        log.setAuthLevel("L3");
        log.setStatus(over ? 3 : 0);
        log.setNote(reason);
        log.setAfterJson(JSON.toJSONString(Collections.singletonMap("reliefId", r.getReliefId())));
        actionLogRepository.save(log);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reliefId", r.getReliefId());
        data.put("status", r.getStatus());
        data.put("statusName", reliefStatusName(r.getStatus()));
        data.put("quota", quota);
        data.put("overQuota", over);
        data.put("message", over
                ? "申请额 ￥" + amount + " 超过你的单笔额度 ￥" + quota + "，已生成证据包，账目未改动——请提交给有权限的人处理"
                : "已提交，等待第二人审批（审批人不能是你自己）");
        return Result.ok(data);
    }

    @Transactional
    @PostMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@RequestHeader("X-Property-Token") String token,
                                               @PathVariable Long id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyBillRelief r = reliefRepository.findById(id).orElseThrow(() -> new BizException("申请不存在"));
        if (r.getStatus() != 0) throw new BizException("当前状态不可审批：" + reliefStatusName(r.getStatus()));
        if (Objects.equals(r.getApplicantId(), admin.getAdminId())) throw new BizException("审批人不能是申请人本人");

        PropertyBill bill = billRepository.findById(r.getBillId()).orElseThrow(() -> new BizException("账单不存在"));
        BigDecimal unpaid = unpaidOf(bill);
        if (r.getApplyAmount().compareTo(unpaid) > 0) throw new BizException("未结清金额已变化，请重新核对后申请");

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("deducted", bill.getDeducted());
        before.put("unpaid", unpaid);

        BigDecimal ded = bill.getDeducted() == null ? BigDecimal.ZERO : bill.getDeducted();
        bill.setDeducted(ded.add(r.getApplyAmount()));
        billRepository.save(bill);

        PropertyDeductLog dl = new PropertyDeductLog();
        dl.setOwnerId(r.getOwnerId());
        dl.setBillId(r.getBillId());
        dl.setDeductAmount(r.getApplyAmount());
        dl.setSourceType(SOURCE_RELIEF);
        dl.setBeforeBalance(ded);
        dl.setAfterBalance(bill.getDeducted());
        deductLogRepository.save(dl);

        r.setStatus(1);
        r.setApproverId(admin.getAdminId());
        r.setApproverName(admin.getAdminName());
        r.setApproveNote(body == null || body.get("note") == null ? null : String.valueOf(body.get("note")));
        reliefRepository.save(r);

        PropertyActionLog log = new PropertyActionLog();
        log.setCompanyId(r.getCompanyId());
        log.setAdminId(admin.getAdminId());
        log.setAdminName(admin.getAdminName());
        log.setAction("relief-approve");
        log.setActionLabel("审批通过减免");
        log.setTargetType("bill");
        log.setTargetId(r.getBillId());
        log.setTargetLabel(bill.getBillNo());
        log.setScopeDesc("减免 ￥" + r.getApplyAmount() + " 计入抵扣，未结清 ￥" + unpaid + " → ￥" + unpaidOf(bill)
                + " · 申请人 " + r.getApplicantName());
        log.setAuthLevel("L3");
        log.setStatus(1);
        log.setBeforeJson(JSON.toJSONString(before));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("deducted", bill.getDeducted());
        after.put("unpaid", unpaidOf(bill));
        after.put("deductLogId", dl.getLogId());
        log.setAfterJson(JSON.toJSONString(after));
        log.setNote(r.getApproveNote());
        log.setUndoDeadline(LocalDateTime.now().plusMinutes(5));
        actionLogRepository.save(log);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reliefId", r.getReliefId());
        data.put("status", r.getStatus());
        data.put("statusName", reliefStatusName(r.getStatus()));
        data.put("billUnpaid", unpaidOf(bill));
        data.put("logId", log.getLogId());
        data.put("undoDeadline", log.getUndoDeadline().toString().replace('T', ' ').substring(0, 19));
        return Result.ok(data);
    }

    @Transactional
    @PostMapping("/{id}/reject")
    public Result<Map<String, Object>> reject(@RequestHeader("X-Property-Token") String token,
                                              @PathVariable Long id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyBillRelief r = reliefRepository.findById(id).orElseThrow(() -> new BizException("申请不存在"));
        if (r.getStatus() != 0 && r.getStatus() != 3) throw new BizException("当前状态不可驳回");
        String note = body == null || body.get("note") == null ? "人工驳回" : String.valueOf(body.get("note"));
        r.setStatus(2);
        r.setApproverId(admin.getAdminId());
        r.setApproverName(admin.getAdminName());
        r.setApproveNote(note);
        reliefRepository.save(r);

        PropertyActionLog log = new PropertyActionLog();
        log.setCompanyId(r.getCompanyId());
        log.setAdminId(admin.getAdminId());
        log.setAdminName(admin.getAdminName());
        log.setAction("relief-reject");
        log.setActionLabel("驳回减免申请");
        log.setTargetType("bill");
        log.setTargetId(r.getBillId());
        log.setTargetLabel("relief#" + r.getReliefId());
        log.setScopeDesc("申请 ￥" + r.getApplyAmount() + " 未执行，账目未改动");
        log.setAuthLevel("L3");
        log.setStatus(3);
        log.setNote(note);
        actionLogRepository.save(log);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reliefId", r.getReliefId());
        data.put("status", r.getStatus());
        data.put("statusName", reliefStatusName(r.getStatus()));
        return Result.ok(data);
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestHeader("X-Property-Token") String token,
                                            @RequestParam(required = false) Long companyId,
                                            @RequestParam(required = false) Integer status) {
        PropertyAdmin admin = validateAdmin(token);
        Long cid = resolveCompany(token, companyId);
        List<PropertyBillRelief> all = status == null
                ? reliefRepository.findByCompanyIdOrderByReliefIdDesc(cid)
                : reliefRepository.findByCompanyIdAndStatusOrderByReliefIdDesc(cid, status);

        List<Map<String, Object>> rows = all.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reliefId", r.getReliefId());
            m.put("billId", r.getBillId());
            m.put("billNo", billRepository.findById(r.getBillId()).map(PropertyBill::getBillNo).orElse("--"));
            m.put("roomLabel", roomLabel(r.getRoomId()));
            m.put("ownerName", ownerRepository.findById(r.getOwnerId()).map(PropertyOwner::getOwnerName).orElse("--"));
            m.put("reliefType", r.getReliefType());
            m.put("applyAmount", r.getApplyAmount());
            m.put("quotaAmount", r.getQuotaAmount());
            m.put("status", r.getStatus());
            m.put("statusName", reliefStatusName(r.getStatus()));
            m.put("applicantName", r.getApplicantName());
            m.put("approverName", r.getApproverName());
            m.put("reason", r.getReason());
            m.put("approveNote", r.getApproveNote());
            m.put("canApprove", r.getStatus() == 0 && !Objects.equals(r.getApplicantId(), admin.getAdminId()));
            m.put("createTime", r.getCreateTime() == null ? null : r.getCreateTime().toString().replace('T', ' ').substring(0, 16));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", rows);
        data.put("total", rows.size());
        data.put("waiting", all.stream().filter(r -> r.getStatus() == 0).count());
        data.put("overQuota", all.stream().filter(r -> r.getStatus() == 3).count());
        data.put("approved", all.stream().filter(r -> r.getStatus() == 1).count());
        data.put("rejected", all.stream().filter(r -> r.getStatus() == 2).count());
        data.put("myRole", roleName(admin.getRoleType()));
        data.put("myQuota", quotaOf(admin));
        return Result.ok(data);
    }

    private String reliefStatusName(Integer s) {
        if (s == null) return "未知";
        switch (s) {
            case 0: return "待审批";
            case 1: return "已通过";
            case 2: return "已驳回";
            case 3: return "超额度·只出证据包";
            default: return "状态" + s;
        }
    }
}
