package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 能力与规则（/api/property/ai）。
 *
 * 后台「AI 值班台 / 能力全景 / 主动规则」三个页面的数据源，全部来自真实业务表：
 *   - 值班台 = 六个域里「今天真该动的那几件」聚合，不是编的待办
 *   - 能力全景 = 14 域 39 项，按「谁按最后那个按钮」分三档（AI 全办 24 / 起草你点头 10 / 只能你做 5）
 *   - 主动规则 = property_ai_rule 表，13 条，带打扰预算
 */
@Slf4j
@RestController
@RequestMapping("/api/property/ai")
@RequiredArgsConstructor
public class PropertyAiController {

    private final PropertyAiRuleRepository ruleRepository;
    private final PropertyWorkorderRepository workorderRepository;
    private final PropertyBillRepository billRepository;
    private final PropertyGroupMsgRepository msgRepository;
    private final PropertyParkingViolationRepository vioRepository;
    private final PropertyParkingTransferRepository transferRepository;
    private final PropertyMerchantReferralRepository referralRepository;
    private final PropertyAdminRepository adminRepository;
    private final PropertyAdminSessionRepository sessionRepository;

    private PropertyAdmin validateAdmin(String token) {
        if (token == null || token.isBlank()) throw new BizException("未登录");
        PropertyAdminSession session = sessionRepository.findByTokenAndExpireTimeAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BizException("登录已过期，请重新登录"));
        return adminRepository.findById(session.getAdminId())
                .orElseThrow(() -> new BizException("管理员不存在"));
    }

    /** admin 侧接口 companyId 可省略：缺省取登录管理员所属公司（与既有后台页面一致） */
    private Long resolveCompany(String token, Long companyId) {
        if (companyId != null) return companyId;
        return validateAdmin(token).getCompanyId();
    }

    // =====================================================================
    // 能力全景：14 域 39 项
    // 顺序：域 / 能力项 / AI全办 / 起草点头 / 只能你做 / 对应后台页面 / 是否本轮新增
    // 合计必须 = 39 / 24 / 10 / 5（有断言保护，改数必须同步改）
    // =====================================================================
    private static final Object[][] DOMAINS = {
            {"组织·房屋·业主",   3, 2, 1, 0, "业主管理 · 楼宇房间", 0},
            {"账单收费",         4, 2, 1, 1, "账单管理 · 业主管理", 0},
            {"余额抵扣",         3, 3, 0, 0, "账单管理 · 抵扣流水", 0},
            {"工单",             4, 2, 1, 1, "维修工单 · 工单详情", 0},
            {"设备告警",         3, 2, 1, 0, "IoT 设备", 0},
            {"巡检",             2, 2, 0, 0, "巡检工单", 0},
            {"门禁·人脸",        3, 1, 1, 1, "门禁管理 · 人脸审核", 0},
            {"车辆·访客·临停",   3, 2, 1, 0, "车辆 · 访客 · 临时停车", 0},
            {"场地·增值",        2, 1, 0, 1, "场地预约 · 活动审核", 0},
            {"商户·分账",        3, 2, 1, 0, "商家绑定 · 分账流水", 0},
            {"开奖·奖池",        2, 1, 1, 0, "开奖配置", 0},
            {"业主群受理",       2, 2, 0, 0, "业主群受理台（本轮新增）", 1},
            {"邻里互助·车位备案", 3, 1, 1, 1, "邻里互助（本轮新增）", 1},
            {"违停曝光",         2, 1, 1, 0, "违停曝光审核（本轮新增）", 1},
    };

    @GetMapping("/ability")
    public Result<Map<String, Object>> ability(@RequestHeader("X-Property-Token") String token,
                                               @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        List<PropertyAiRule> rules = rulesOf(companyId);

        int items = 0, auto = 0, draft = 0, manual = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] d : DOMAINS) {
            int n = (Integer) d[1], a = (Integer) d[2], dr = (Integer) d[3], ma = (Integer) d[4];
            if (n != a + dr + ma) {
                throw new BizException("能力口径不一致：" + d[0] + " " + n + " != " + a + "+" + dr + "+" + ma);
            }
            items += n; auto += a; draft += dr; manual += ma;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("domain", d[0]);
            m.put("items", n);
            m.put("auto", a);
            m.put("draft", dr);
            m.put("manual", ma);
            m.put("pages", d[5]);
            m.put("isNew", d[6]);
            rows.add(m);
        }
        if (items != 39) throw new BizException("能力项合计应为 39，实际 " + items);

        // 今天真动过的：用规则表本周触发数汇总，代表引擎实际跑过的量
        int triggered = rules.stream().mapToInt(r -> r.getWeekCount() == null ? 0 : r.getWeekCount()).sum();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("domains", rows);
        out.put("domainCount", rows.size());
        out.put("itemCount", items);
        out.put("autoCount", auto);
        out.put("draftCount", draft);
        out.put("manualCount", manual);
        out.put("triggeredThisWeek", triggered);
        return Result.ok(out);
    }

    @GetMapping("/rules")
    public Result<List<Map<String, Object>>> rules(@RequestHeader("X-Property-Token") String token,
                                                   @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        List<PropertyAiRule> list = rulesOf(companyId);
        return Result.ok(list.stream().map(this::ruleMap).collect(Collectors.toList()));
    }

    @PostMapping("/rules/{ruleId}/toggle")
    public Result<Map<String, Object>> toggle(@RequestHeader("X-Property-Token") String token,
                                              @PathVariable Long ruleId) {
        validateAdmin(token);
        PropertyAiRule r = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new BizException("规则不存在"));
        r.setStatus(Integer.valueOf(1).equals(r.getStatus()) ? 0 : 1);
        ruleRepository.save(r);
        return Result.ok(ruleMap(r));
    }

    @PostMapping("/rules/{ruleId}")
    public Result<Map<String, Object>> updateRule(@RequestHeader("X-Property-Token") String token,
                                                  @PathVariable Long ruleId,
                                                  @RequestParam(required = false) String triggerDesc,
                                                  @RequestParam(required = false) String actionDesc,
                                                  @RequestParam(required = false) String budgetDesc,
                                                  @RequestParam(required = false) Integer authorizeLevel,
                                                  @RequestParam(required = false) Integer status) {
        validateAdmin(token);
        PropertyAiRule r = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new BizException("规则不存在"));
        if (triggerDesc != null) r.setTriggerDesc(triggerDesc);
        if (actionDesc != null) r.setActionDesc(actionDesc);
        if (budgetDesc != null) r.setBudgetDesc(budgetDesc);
        if (authorizeLevel != null) {
            if (authorizeLevel == 3) {
                throw new BizException("L3「只能你做」不能由界面改档，需三位角色会签");
            }
            r.setAuthorizeLevel(authorizeLevel);
        }
        if (status != null) r.setStatus(status);
        ruleRepository.save(r);
        return Result.ok(ruleMap(r));
    }

    // =====================================================================
    // 值班台：把六个域「今天该动的」聚成待确认队列
    // =====================================================================
    @GetMapping("/desk")
    public Result<Map<String, Object>> desk(@RequestHeader("X-Property-Token") String token,
                                            @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        final Long cid = resolveCompany(token, companyId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        List<Map<String, Object>> cards = new ArrayList<>();

        // 1. 工单超时未派
        List<PropertyWorkorder> wos = workorderRepository.findByCompanyIdAndStatusInOrderByCreateTimeDesc(
                cid, Arrays.asList(0, 5));
        List<PropertyWorkorder> timeout = wos.stream()
                .filter(w -> w.getCreateTime() != null && Duration.between(w.getCreateTime(), now).toMinutes() > 30)
                .collect(Collectors.toList());
        if (!timeout.isEmpty()) {
            long maxMin = timeout.stream().mapToLong(w -> Duration.between(w.getCreateTime(), now).toMinutes()).max().orElse(0);
            cards.add(card("紧急", "danger", timeout.size() + " 张工单超时未派 · 最久 " + (maxMin / 60) + " 小时",
                    "L2 草稿", "lv2",
                    new String[][]{{"依据", "维修工单 · 超 30 分钟未接单 · 共 " + timeout.size() + " 单"},
                            {"影响面", "超 4 小时会触发业主催单；跨工种的需你拆单"}},
                    new String[][]{{"确认派单", "pri"}, {"改一下", "gh"}, {"不用了", "tx"}}, "约 30 秒"));
        }

        // 2. 待收账单
        List<PropertyBill> bills = billRepository.findAll().stream()
                .filter(b -> cid.equals(b.getCompanyId()))
                .collect(Collectors.toList());
        BigDecimal unpaid = BigDecimal.ZERO;
        int unpaidCount = 0;
        for (PropertyBill b : bills) {
            BigDecimal amt = nz(b.getAmount()).subtract(nz(b.getDeducted())).subtract(nz(b.getPaid()));
            if (amt.compareTo(BigDecimal.ZERO) > 0) {
                unpaid = unpaid.add(amt);
                unpaidCount++;
            }
        }
        if (unpaidCount > 0) {
            cards.add(card("今日", "brand", "待收 ￥" + unpaid.setScale(2, BigDecimal.ROUND_HALF_UP) + " · 涉及 " + unpaidCount + " 户",
                    "L3 群发", "lv3",
                    new String[][]{{"依据", "账单 + 缴费记录 · 未结清 " + unpaidCount + " 户"},
                            {"影响面", "已抵清的户不发催缴；发出后不可撤回"}},
                    new String[][]{{"看名单", "pri"}, {"先发 3 户试", "gh"}}, "约 1 分钟"));
        }

        // 3. 群待判断
        long groupPending = msgRepository.countByCompanyIdAndReceiptStatusAndMsgTimeAfter(cid, 2, dayStart);
        long groupAccepted = msgRepository.countByCompanyIdAndReceiptStatusAndMsgTimeAfter(cid, 1, dayStart);
        if (groupPending > 0) {
            cards.add(card("群", "accent", "业主群 " + groupPending + " 条待你判断 · 已静默受理 " + groupAccepted + " 条",
                    "L2 草稿", "lv2",
                    new String[][]{{"依据", "群引擎 · 今日需求 · 主观投诉不落单"},
                            {"影响面", "判错可撤回并补一条更正；不替物业给业主定罪"}},
                    new String[][]{{"逐条看", "pri"}, {"全部不建单", "gh"}}, "约 20 秒"));
        }

        // 4. 违停待审曝光
        long vioReview = vioRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(cid, 2).size();
        if (vioReview > 0) {
            cards.add(card("违停", "danger", vioReview + " 条曝光稿待审 · 本月曝光位剩 "
                            + Math.max(0, 3 - countExposed(cid)) + " 条",
                    "L2 草稿", "lv2",
                    new String[][]{{"依据", "随手拍 · 无登记车 · 已起草曝光稿（车牌打星）"},
                            {"影响面", "曝光位每月上限 3 条；上多了业主反感，威慑力反而下降"}},
                    new String[][]{{"审曝光稿", "pri"}, {"仅贴单", "gh"}}, "约 15 秒"));
        }

        // 5. 车位备案卡在中间
        List<PropertyParkingTransfer> transfers = transferRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(cid, 0);
        if (!transfers.isEmpty()) {
            long stuck = transfers.stream().filter(t -> !Integer.valueOf(1).equals(t.getStepWhitelist())).count();
            cards.add(card("备案", "accent", stuck + " 个车位转让卡在中间 · 道闸进不去业主会来投诉",
                    "L2 双方确认", "lv2",
                    new String[][]{{"依据", "互助挂牌成交后生成备案单 · 三步未走完"},
                            {"影响面", "改月租账户 + 放白名单后次日生效"}},
                    new String[][]{{"去推进", "pri"}, {"看详情", "gh"}}, "约 1 分钟"));
        }

        // 6. 商家邀请待过审
        List<PropertyMerchantReferral> referrals = referralRepository
                .findByCompanyIdAndStatusOrderByCreateTimeDesc(cid, 1);
        if (!referrals.isEmpty()) {
            cards.add(card("邀请", "brand", referrals.size() + " 个商家待过审 · 过审即 ¥30 到账",
                    "L2 草稿", "lv2",
                    new String[][]{{"依据", "邀请码已扫码 · 执照 OCR 待复核"},
                            {"影响面", "过审后商家立刻拿到存量后台账号；¥70 绑首单与 7 天退款观察期"}},
                    new String[][]{{"去复核", "pri"}, {"催办", "gh"}}, "约 10 分钟"));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cards", cards);
        out.put("pendingCount", cards.size());
        out.put("todayDemand", msgRepository.countByCompanyIdAndIsDemandAndMsgTimeAfter(cid, 1, dayStart));
        return Result.ok(out);
    }

    // ================== 工具 ==================

    private List<PropertyAiRule> rulesOf(Long companyId) {
        List<PropertyAiRule> mine = ruleRepository.findByCompanyIdOrderBySortNoAsc(companyId);
        if (!mine.isEmpty()) return mine;
        return ruleRepository.findByCompanyIdOrderBySortNoAsc(0L);
    }

    private long countExposed(Long companyId) {
        LocalDateTime monthStart = LocalDateTime.now().toLocalDate().withDayOfMonth(1).atStartOfDay();
        return vioRepository.findByCompanyIdOrderByCreateTimeDesc(companyId).stream()
                .filter(v -> Integer.valueOf(3).equals(v.getStatus())
                        && v.getCreateTime() != null && v.getCreateTime().isAfter(monthStart))
                .count();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Map<String, Object> card(String tag, String tone, String head, String level, String lvcls,
                                     String[][] rows, String[][] btns, String eta) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tag", tag);
        m.put("tone", tone);
        m.put("head", head);
        m.put("level", level);
        m.put("lvcls", lvcls);
        List<Map<String, Object>> rs = new ArrayList<>();
        for (String[] r : rows) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("k", r[0]);
            x.put("v", r[1]);
            rs.add(x);
        }
        m.put("rows", rs);
        List<Map<String, Object>> bs = new ArrayList<>();
        for (String[] b : btns) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("label", b[0]);
            x.put("cls", b[1]);
            bs.add(x);
        }
        m.put("btns", bs);
        m.put("eta", eta);
        return m;
    }

    private Map<String, Object> ruleMap(PropertyAiRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleId", r.getRuleId());
        m.put("eventCode", r.getEventCode());
        m.put("eventName", r.getEventName());
        m.put("triggerDesc", r.getTriggerDesc());
        m.put("actionDesc", r.getActionDesc());
        m.put("authorizeLevel", r.getAuthorizeLevel());
        m.put("budgetDesc", r.getBudgetDesc());
        m.put("weekCount", r.getWeekCount());
        m.put("status", r.getStatus());
        m.put("sortNo", r.getSortNo());
        return m;
    }
}
