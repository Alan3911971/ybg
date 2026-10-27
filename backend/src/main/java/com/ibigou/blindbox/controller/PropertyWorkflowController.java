package com.ibigou.blindbox.controller;

import com.alibaba.fastjson.JSON;
import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import com.ibigou.blindbox.service.PropertyWorkorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工单处置：四因子打分 + 按建议派单 + 跨工种拆单（/api/property/workflow）。
 *
 * 四因子写死在引擎里，不由模型临场决定（与设计稿「这三档怎么判的（写死，不靠提示词）」一致）：
 *   ① 紧急度 0-35   报修紧急标记 + 事故类关键词（电梯关人/漏水/燃气/消防/断电）
 *   ② 影响面 0-30   户内 1 户 / 单元公共 / 整栋 / 全小区；业主群多人指向同一问题加权
 *   ③ 时效压力 0-20 已等待时长 + 该单超时次数
 *   ④ 技能难度 0-15 需特种设备证（电梯/消防）> 水电 > 保洁绿化；难的单子先排，避免拖到没人能接
 * 合计 100，≥80 P0 立即 / 60-79 P1 今日 / <60 P2 本周。
 *
 * 派单建议只给「历史真承担过该工种」的人，按当前在手单量升序——没有池子就明说没有，不编人名。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/workflow")
@RequiredArgsConstructor
public class PropertyWorkflowController {

    private final PropertyWorkorderRepository workorderRepository;
    private final PropertyWorkorderService workorderService;
    private final PropertyRoomRepository roomRepository;
    private final PropertyBuildingRepository buildingRepository;
    private final PropertyAdminRepository adminRepository;
    private final PropertyAdminSessionRepository sessionRepository;
    private final PropertyActionLogRepository actionLogRepository;

    /** 工种 → 关键词（用于拆单与技能匹配） */
    private static final String[][] TRADES = {
            {"电梯", "电梯", "扶梯", "关人", "平层", "曳引"},
            {"水暖", "漏水", "渗水", "水管", "下水", "堵", "马桶", "角阀", "暖气", "热水"},
            {"电工", "断电", "跳闸", "短路", "灯", "插座", "电表", "线路", "配电"},
            {"土建", "墙", "裂缝", "门", "窗", "锁", "地砖", "防水", "天花"},
            {"秩序", "消防", "通道", "违停", "门禁", "道闸", "监控", "保安"},
            {"保洁", "垃圾", "清扫", "异味", "消杀", "绿化", "除草"},
    };

    /** 事故类关键词：命中即抬紧急度（这些词出现，任何"顺延处理"都是错的） */
    private static final String[] ACCIDENT_KW = {"关人", "困人", "漏水", "冒烟", "起火", "燃气", "煤气", "漏电", "触电", "消防", "停水", "停电", "倒塌"};

    /** 影响面关键词：公共区域 = 不止一户 */
    private static final String[] PUBLIC_KW = {"电梯", "楼道", "单元", "大堂", "车库", "地库", "消防通道", "外墙", "屋顶", "水管", "总阀", "路灯", "门禁"};

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

    private PropertyWorkorder mustWo(Long id) {
        return workorderRepository.findById(id).orElseThrow(() -> new BizException("工单不存在"));
    }

    // =====================================================================
    // 四因子评分
    // =====================================================================

    /** 命中关键词（返回命中的第一个工种，没有返回 null） */
    private String guessTrade(String text) {
        if (text == null) return null;
        for (String[] t : TRADES) {
            for (int i = 1; i < t.length; i++) {
                if (text.contains(t[i])) return t[0];
            }
        }
        return null;
    }

    /** 命中的全部工种（用于判断是否需要拆单） */
    private List<String> guessTrades(String text) {
        List<String> hit = new ArrayList<>();
        if (text == null) return hit;
        for (String[] t : TRADES) {
            for (int i = 1; i < t.length; i++) {
                if (text.contains(t[i])) { hit.add(t[0]); break; }
            }
        }
        return hit;
    }

    private String roomLabel(Long roomId) {
        if (roomId == null) return null;
        PropertyRoom room = roomRepository.findById(roomId).orElse(null);
        if (room == null) return null;
        String bName = buildingRepository.findById(room.getBuildingId()).map(PropertyBuilding::getBuildingName).orElse("");
        return (bName.isEmpty() ? "" : bName) + (room.getUnitNo() == null ? "" : room.getUnitNo() + "-")
                + (room.getFloorNo() == null ? "" : room.getFloorNo()) + "-"
                + (room.getRoomNo() == null ? "" : room.getRoomNo());
    }

    private Map<String, Object> scoreOf(PropertyWorkorder wo) {
        String text = (wo.getTitle() == null ? "" : wo.getTitle()) + " "
                + (wo.getDescription() == null ? "" : wo.getDescription()) + " "
                + (wo.getCategory() == null ? "" : wo.getCategory());

        // ① 紧急度 0-35
        int urgency = wo.getUrgency() == null ? 1 : wo.getUrgency();
        int f1 = urgency >= 3 ? 35 : urgency == 2 ? 22 : 12;
        List<String> accHit = new ArrayList<>();
        for (String k : ACCIDENT_KW) if (text.contains(k)) accHit.add(k);
        if (!accHit.isEmpty() && f1 < 35) f1 = Math.min(35, f1 + 10);
        String f1Reason = "报修标记" + (urgency >= 3 ? "紧急" : urgency == 2 ? "较急" : "普通")
                + (accHit.isEmpty() ? "" : "；事故类关键词命中：" + String.join("/", accHit));

        // ② 影响面 0-30
        int f2;
        List<String> pubHit = new ArrayList<>();
        for (String k : PUBLIC_KW) if (text.contains(k)) pubHit.add(k);
        String lbl = roomLabel(wo.getRoomId());
        if (!pubHit.isEmpty()) f2 = text.contains("全小区") || text.contains("小区") ? 30 : 20;
        else f2 = wo.getRoomId() == null ? 14 : 8;
        String f2Reason = (wo.getRoomId() != null && pubHit.isEmpty() ? "户内单户（" + (lbl == null ? "房号待补" : lbl) + "）"
                : wo.getRoomId() == null ? "未定位到房号，按公共面估算"
                : "公共部位：" + String.join("/", pubHit))
                + "；来源 " + ("group".equals(wo.getSource()) ? "业主群多人可见" : "owner".equals(wo.getSource()) ? "业主端单聊" : String.valueOf(wo.getSource()));

        // ③ 时效压力 0-20
        long mins = 0;
        if (wo.getCreateTime() != null) mins = Math.max(0, Duration.between(wo.getCreateTime(), LocalDateTime.now()).toMinutes());
        int f3 = mins <= 120 ? 4 : mins <= 480 ? 10 : mins <= 1440 ? 14 : 20;
        int timeout = wo.getTimeoutCount() == null ? 0 : wo.getTimeoutCount();
        if (timeout > 0) f3 = Math.min(20, f3 + timeout * 3);
        String f3Reason = "已等待 " + (mins < 60 ? mins + " 分钟" : (mins / 60) + " 小时")
                + (timeout > 0 ? "；超时 " + timeout + " 次" : "；未超时");

        // ④ 技能难度 0-15
        String trade = guessTrade(text);
        int f4;
        String f4Reason;
        if ("电梯".equals(trade)) { f4 = 15; f4Reason = "电梯：需特种设备作业证，能接的人少，排后无人能补"; }
        else if ("秩序".equals(trade) && (text.contains("消防") || text.contains("通道"))) { f4 = 14; f4Reason = "消防通道：涉及安全责任，需保安到场并留证"; }
        else if ("水暖".equals(trade) || "电工".equals(trade)) { f4 = 8; f4Reason = trade + "：常规工种，可当日排"; }
        else if ("土建".equals(trade)) { f4 = 6; f4Reason = "土建：影响观感不影响使用，可排期"; }
        else { f4 = 3; f4Reason = (trade == null ? "未识别工种" : trade + "：可延后") + "，流程短"; }

        int total = f1 + f2 + f3 + f4;
        String level = total >= 80 ? "P0" : total >= 60 ? "P1" : "P2";
        String levelName = total >= 80 ? "立即处理" : total >= 60 ? "今日处理" : "本周处理";
        String eta = total >= 80 ? "2 小时内到场" : total >= 60 ? "今日 18:00 前" : "本周内安排";

        List<Map<String, Object>> factors = new ArrayList<>();
        factors.add(factor("urgency", "紧急度", f1, 35, f1Reason));
        factors.add(factor("scope", "影响面", f2, 30, f2Reason));
        factors.add(factor("time", "时效压力", f3, 20, f3Reason));
        factors.add(factor("skill", "技能难度", f4, 15, f4Reason));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("factors", factors);
        m.put("total", total);
        m.put("level", level);
        m.put("levelName", levelName);
        m.put("eta", eta);
        m.put("trade", trade);
        // 拆单判断：命中 ≥2 个工种 → 一条单塞了两个工种，派给谁都得返修
        List<String> trades = guessTrades(text);
        m.put("needSplit", trades.size() >= 2);
        List<Map<String, Object>> splitItems = new ArrayList<>();
        if (trades.size() >= 2) {
            for (String t : trades) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("trade", t);
                it.put("title", t + "：" + shortTitle(wo.getTitle()));
                it.put("reason", "原文命中「" + t + "」相关关键词，属于独立工种");
                splitItems.add(it);
            }
        }
        m.put("splitItems", splitItems);
        return m;
    }

    private String shortTitle(String t) {
        if (t == null) return "";
        String s = t.replace("群内报修·", "").replace("群内投诉·", "");
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private Map<String, Object> factor(String key, String name, int score, int max, String reason) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("key", key);
        f.put("name", name);
        f.put("score", score);
        f.put("max", max);
        f.put("reason", reason);
        return f;
    }

    /**
     * 派单建议：候选人 = 本公司历史承担过该工种的人（property_workorder.assignee_name 聚合），
     * 按当前在手单量升序。池子为空就返回空——绝不编一个名字出来。
     */
    private Map<String, Object> suggestAssignee(Long companyId, String trade) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (trade == null) {
            r.put("assigneeName", null);
            r.put("reason", "未识别工种，请手动指定");
            r.put("candidates", Collections.emptyList());
            return r;
        }
        List<PropertyWorkorder> all = workorderRepository.findAll().stream()
                .filter(w -> companyId == null || companyId.equals(w.getCompanyId()))
                .collect(Collectors.toList());
        Map<String, Integer> load = new HashMap<>();
        Map<String, Integer> done = new HashMap<>();
        for (PropertyWorkorder w : all) {
            String name = w.getAssigneeName();
            if (name == null || name.isBlank()) continue;
            if (w.getStatus() != null && (w.getStatus() == 0 || w.getStatus() == 1)) {
                load.merge(name, 1, Integer::sum);
            }
            String t = guessTrade((w.getTitle() == null ? "" : w.getTitle()) + " " + (w.getCategory() == null ? "" : w.getCategory()));
            if (trade.equals(t)) done.merge(name, 1, Integer::sum);
        }
        List<Map<String, Object>> cands = new ArrayList<>();
        for (Map.Entry<String, Integer> e : done.entrySet()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", e.getKey());
            c.put("matched", e.getValue());
            c.put("load", load.getOrDefault(e.getKey(), 0));
            cands.add(c);
        }
        cands.sort(Comparator.comparingInt(c -> (Integer) c.get("load")));
        r.put("candidates", cands);
        if (cands.isEmpty()) {
            r.put("assigneeName", null);
            r.put("reason", "本公司无「" + trade + "」工种的派单记录，请先在下方手动指定负责人，或到「业主管理」维护维修工");
        } else {
            Map<String, Object> best = cands.get(0);
            r.put("assigneeName", best.get("name"));
            r.put("reason", best.get("name") + " 承担过 " + best.get("matched") + " 单" + trade
                    + "，当前在手 " + best.get("load") + " 单（同工种里最空）");
        }
        return r;
    }

    /** 单张工单的完整处置面板：工单本身 + 四因子 + 派单建议 + 拆单建议 + 依据 */
    @GetMapping("/workorder/{id}/score")
    public Result<Map<String, Object>> score(@RequestHeader("X-Property-Token") String token,
                                             @PathVariable Long id,
                                             @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        PropertyWorkorder wo = mustWo(id);
        Long cid = resolveCompany(token, companyId);
        Map<String, Object> sc = scoreOf(wo);

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("woId", wo.getWoId());
        w.put("woNo", wo.getWoNo());
        w.put("title", wo.getTitle());
        w.put("description", wo.getDescription());
        w.put("woType", wo.getWoType());
        w.put("woTypeName", wo.getWoType() != null && wo.getWoType() == 2 ? "投诉" : "报修");
        w.put("category", wo.getCategory());
        w.put("urgency", wo.getUrgency());
        w.put("status", wo.getStatus());
        w.put("statusName", statusName(wo.getStatus()));
        w.put("assigneeName", wo.getAssigneeName());
        w.put("source", wo.getSource());
        w.put("sourceName", sourceName(wo.getSource()));
        w.put("sourceRef", wo.getSourceRef());
        w.put("roomLabel", roomLabel(wo.getRoomId()));
        w.put("images", wo.getImages());
        w.put("media", wo.getMedia());
        w.put("timeoutCount", wo.getTimeoutCount());
        w.put("createTime", wo.getCreateTime() == null ? null : wo.getCreateTime().toString().replace('T', ' '));
        w.put("waitedMin", wo.getCreateTime() == null ? 0 : Duration.between(wo.getCreateTime(), LocalDateTime.now()).toMinutes());
        data.put("wo", w);

        data.put("factors", sc.get("factors"));
        data.put("total", sc.get("total"));
        data.put("level", sc.get("level"));
        data.put("levelName", sc.get("levelName"));
        data.put("eta", sc.get("eta"));
        data.put("trade", sc.get("trade"));
        data.put("needSplit", sc.get("needSplit"));
        data.put("splitItems", sc.get("splitItems"));
        data.put("suggest", suggestAssignee(cid, (String) sc.get("trade")));

        // 依据：同房历史单 + 同类工单近 30 天次数（AI 结论必须可点开数据源）
        List<Map<String, Object>> history = workorderRepository.findByOwnerIdOrderByCreateTimeDesc(wo.getOwnerId()).stream()
                .filter(h -> !h.getWoId().equals(wo.getWoId()))
                .limit(5)
                .map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("woNo", h.getWoNo());
                    m.put("title", h.getTitle());
                    m.put("statusName", statusName(h.getStatus()));
                    m.put("createTime", h.getCreateTime() == null ? null : h.getCreateTime().toString().replace('T', ' ').substring(0, 16));
                    return m;
                }).collect(Collectors.toList());
        data.put("sameOwnerHistory", history);

        // 该单的处置留痕（谁派过、改派过）
        data.put("logs", actionLogRepository
                .findByCompanyIdAndTargetTypeAndTargetIdOrderByLogIdDesc(cid, "workorder", id)
                .stream().limit(10).map(this::logMap).collect(Collectors.toList()));

        data.put("ruleNote", "四因子与三档判法写死在引擎里，不由模型临场决定；改档位会在执行记录里留痕。");
        return Result.ok(data);
    }

    /** 待确认队列：所有未派单工单按四因子总分降序（页面「待你确认」列表） */
    @GetMapping("/queue")
    public Result<Map<String, Object>> queue(@RequestHeader("X-Property-Token") String token,
                                             @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        Long cid = resolveCompany(token, companyId);
        List<Map<String, Object>> rows = new ArrayList<>();
        int p0 = 0, needSplit = 0;
        long sumMin = 0;
        for (PropertyWorkorder wo : workorderRepository.findByCompanyIdAndStatusInOrderByCreateTimeDesc(cid, Arrays.asList(0, 5))) {
            Map<String, Object> sc = scoreOf(wo);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("woId", wo.getWoId());
            r.put("woNo", wo.getWoNo());
            r.put("title", shortTitle(wo.getTitle()));
            r.put("woType", wo.getWoType());
            r.put("trade", sc.get("trade"));
            r.put("total", sc.get("total"));
            r.put("level", sc.get("level"));
            r.put("levelName", sc.get("levelName"));
            r.put("eta", sc.get("eta"));
            r.put("needSplit", sc.get("needSplit"));
            r.put("assigneeName", wo.getAssigneeName());
            r.put("source", wo.getSource());
            r.put("createTime", wo.getCreateTime() == null ? null : wo.getCreateTime().toString().replace('T', ' ').substring(0, 16));
            r.put("waitedMin", wo.getCreateTime() == null ? 0 : Duration.between(wo.getCreateTime(), LocalDateTime.now()).toMinutes());
            rows.add(r);
            if ("P0".equals(sc.get("level"))) p0++;
            if (Boolean.TRUE.equals(sc.get("needSplit"))) needSplit++;
            sumMin += (Long) r.get("waitedMin");
        }
        rows.sort((a, b) -> (Integer) b.get("total") - (Integer) a.get("total"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", rows);
        data.put("total", rows.size());
        data.put("p0", p0);
        data.put("needSplit", needSplit);
        data.put("avgWaitMin", rows.isEmpty() ? 0 : sumMin / rows.size());
        return Result.ok(data);
    }

    /**
     * 按建议派单（确认）。
     * L2：会外发到维修工、会影响业主感知，但不动账 —— 记 action log，5 分钟内可撤回。
     */
    @PostMapping("/workorder/{id}/dispatch")
    public Result<Map<String, Object>> dispatch(@RequestHeader("X-Property-Token") String token,
                                                @PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyWorkorder wo = mustWo(id);
        String assignee = body.get("assigneeName") == null ? null : String.valueOf(body.get("assigneeName"));
        if (assignee == null || assignee.isBlank()) {
            // 没指定就用建议的人；建议里也没有就报错，不偷偷派给谁
            Map<String, Object> sug = suggestAssignee(resolveCompany(token, null), (String) scoreOf(wo).get("trade"));
            assignee = (String) sug.get("assigneeName");
            if (assignee == null || assignee.isBlank()) throw new BizException((String) sug.get("reason"));
        }
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        Map<String, Object> sc = scoreOf(wo);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", wo.getStatus());
        before.put("assigneeName", wo.getAssigneeName());

        workorderService.assignWorkorder(id, null, assignee, admin.getAdminName());
        PropertyWorkorder after = mustWo(id);

        PropertyActionLog log = new PropertyActionLog();
        log.setCompanyId(after.getCompanyId() == null ? admin.getCompanyId() : after.getCompanyId());
        log.setAdminId(admin.getAdminId());
        log.setAdminName(admin.getAdminName());
        log.setAction("dispatch");
        log.setActionLabel("按四因子建议派单");
        log.setTargetType("workorder");
        log.setTargetId(id);
        log.setTargetLabel(after.getWoNo() + " · " + shortTitle(after.getTitle()));
        log.setScopeDesc(sc.get("level") + " " + sc.get("levelName") + " · 派给 " + assignee + " · 通知 1 人，不影响业主账目");
        log.setAuthLevel("L2");
        log.setStatus(1);
        log.setBeforeJson(JSON.toJSONString(before));
        Map<String, Object> afterJson = new LinkedHashMap<>();
        afterJson.put("status", after.getStatus());
        afterJson.put("assigneeName", after.getAssigneeName());
        afterJson.put("total", sc.get("total"));
        afterJson.put("factors", sc.get("factors"));
        afterJson.put("note", note);
        log.setAfterJson(JSON.toJSONString(afterJson));
        log.setNote(note);
        log.setUndoDeadline(LocalDateTime.now().plusMinutes(5));
        actionLogRepository.save(log);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logId", log.getLogId());
        data.put("assigneeName", after.getAssigneeName());
        data.put("status", after.getStatus());
        data.put("statusName", statusName(after.getStatus()));
        data.put("undoDeadline", log.getUndoDeadline().toString().replace('T', ' ').substring(0, 19));
        return Result.ok(data);
    }

    /**
     * 跨工种拆单：一条原文命中 ≥2 个工种 → 拆成 N 条子单，原单关闭。
     * 子单继承来源与房号，各自派给对应工种的人（没池子的留空，交给人工）。
     */
    @PostMapping("/workorder/{id}/split")
    public Result<Map<String, Object>> split(@RequestHeader("X-Property-Token") String token,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyWorkorder wo = mustWo(id);
        Object raw = body.get("items");
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Map) {
                    @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) o;
                    items.add(mm);
                }
            }
        }
        if (items.isEmpty()) throw new BizException("请至少选择要拆出的工种");
        Long cid = wo.getCompanyId() == null ? admin.getCompanyId() : wo.getCompanyId();

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("woNo", wo.getWoNo());
        before.put("title", wo.getTitle());
        before.put("status", wo.getStatus());

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, Object> it : items) {
            String trade = it.get("trade") == null ? null : String.valueOf(it.get("trade"));
            String title = it.get("title") == null ? (trade + "：" + shortTitle(wo.getTitle())) : String.valueOf(it.get("title"));
            PropertyWorkorder sub = new PropertyWorkorder();
            sub.setWoNo("WO" + System.currentTimeMillis() + (int) (Math.random() * 90 + 10));
            sub.setOwnerId(wo.getOwnerId());
            sub.setMemberId(wo.getMemberId());
            sub.setCompanyId(cid);
            sub.setRoomId(wo.getRoomId());
            sub.setWoType(wo.getWoType());
            sub.setTitle(title);
            sub.setDescription("拆自 " + wo.getWoNo() + "（" + shortTitle(wo.getTitle()) + "）\n" + (wo.getDescription() == null ? "" : wo.getDescription()));
            sub.setImages(wo.getImages());
            sub.setMedia(wo.getMedia());
            sub.setSource("split");
            sub.setSourceRef(wo.getWoNo());
            sub.setUrgency(wo.getUrgency());
            sub.setCategory(trade);
            sub.setStatus(0);
            Map<String, Object> sug = suggestAssignee(cid, trade);
            Object nameObj = sug.get("assigneeName");
            if (nameObj != null) {
                sub.setAssigneeName(String.valueOf(nameObj));
                sub.setStatus(1);
            }
            workorderRepository.save(sub);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("woId", sub.getWoId());
            c.put("woNo", sub.getWoNo());
            c.put("trade", trade);
            c.put("title", title);
            c.put("assigneeName", sub.getAssigneeName());
            created.add(c);
            // 子单单独留痕（同一 batchNo）
            PropertyActionLog subLog = new PropertyActionLog();
            subLog.setCompanyId(cid);
            subLog.setAdminId(admin.getAdminId());
            subLog.setAdminName(admin.getAdminName());
            subLog.setAction("dispatch");
            subLog.setActionLabel("拆单后派单");
            subLog.setTargetType("workorder");
            subLog.setTargetId(sub.getWoId());
            subLog.setTargetLabel(sub.getWoNo() + " · " + shortTitle(title));
            subLog.setScopeDesc("拆自 " + wo.getWoNo() + " · 工种 " + trade + " · " + (sub.getAssigneeName() == null ? "待人工指定" : "派给 " + sub.getAssigneeName()));
            subLog.setAuthLevel("L2");
            subLog.setStatus(1);
            subLog.setAfterJson(JSON.toJSONString(c));
            subLog.setUndoDeadline(LocalDateTime.now().plusMinutes(5));
            actionLogRepository.save(subLog);
        }

        wo.setStatus(4);
        workorderRepository.save(wo);

        PropertyActionLog log = new PropertyActionLog();
        log.setCompanyId(cid);
        log.setAdminId(admin.getAdminId());
        log.setAdminName(admin.getAdminName());
        log.setAction("split");
        log.setActionLabel("跨工种拆单");
        log.setTargetType("workorder");
        log.setTargetId(id);
        log.setTargetLabel(wo.getWoNo() + " · " + shortTitle(wo.getTitle()));
        log.setScopeDesc("1 单拆成 " + created.size() + " 单，原单关闭；子单各自派工");
        log.setAuthLevel("L2");
        log.setStatus(1);
        log.setBeforeJson(JSON.toJSONString(before));
        log.setAfterJson(JSON.toJSONString(created));
        log.setUndoDeadline(LocalDateTime.now().plusMinutes(5));
        actionLogRepository.save(log);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logId", log.getLogId());
        data.put("created", created);
        data.put("originStatus", 4);
        data.put("originStatusName", statusName(4));
        data.put("undoDeadline", log.getUndoDeadline().toString().replace('T', ' ').substring(0, 19));
        return Result.ok(data);
    }

    /** 「不用了」：驳回建议，不留动作（只留一条驳回记录，证明 AI 建议被看过） */
    @PostMapping("/workorder/{id}/dismiss")
    public Result<Map<String, Object>> dismiss(@RequestHeader("X-Property-Token") String token,
                                               @PathVariable Long id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyWorkorder wo = mustWo(id);
        String reason = body == null || body.get("reason") == null ? "人工判断不需要派单" : String.valueOf(body.get("reason"));
        PropertyActionLog log = new PropertyActionLog();
        log.setCompanyId(wo.getCompanyId() == null ? admin.getCompanyId() : wo.getCompanyId());
        log.setAdminId(admin.getAdminId());
        log.setAdminName(admin.getAdminName());
        log.setAction("dismiss");
        log.setActionLabel("驳回派单建议");
        log.setTargetType("workorder");
        log.setTargetId(id);
        log.setTargetLabel(wo.getWoNo() + " · " + shortTitle(wo.getTitle()));
        log.setScopeDesc("未产生任何变更");
        log.setAuthLevel("L2");
        log.setStatus(3);
        log.setNote(reason);
        actionLogRepository.save(log);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logId", log.getLogId());
        return Result.ok(data);
    }

    // =====================================================================
    private String statusName(Integer s) {
        if (s == null) return "未知";
        switch (s) {
            case 0: return "待接单";
            case 1: return "处理中";
            case 2: return "待验收";
            case 3: return "已完成";
            case 4: return "已关闭";
            case 5: return "待派单";
            default: return "状态" + s;
        }
    }

    private String sourceName(String s) {
        if ("group".equals(s)) return "业主群";
        if ("owner".equals(s)) return "业主端";
        if ("split".equals(s)) return "拆单";
        if ("mutual".equals(s)) return "邻里互助";
        if ("violation".equals(s)) return "随手拍";
        return s == null ? "未知" : s;
    }

    private Map<String, Object> logMap(PropertyActionLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logId", l.getLogId());
        m.put("adminName", l.getAdminName());
        m.put("action", l.getAction());
        m.put("actionLabel", l.getActionLabel());
        m.put("targetLabel", l.getTargetLabel());
        m.put("scopeDesc", l.getScopeDesc());
        m.put("authLevel", l.getAuthLevel());
        m.put("status", l.getStatus());
        m.put("note", l.getNote());
        m.put("createTime", l.getCreateTime() == null ? null : l.getCreateTime().toString().replace('T', ' ').substring(0, 19));
        m.put("undoDeadline", l.getUndoDeadline() == null ? null : l.getUndoDeadline().toString().replace('T', ' ').substring(0, 19));
        m.put("canUndo", l.getUndoDeadline() != null && LocalDateTime.now().isBefore(l.getUndoDeadline()) && l.getStatus() != null && l.getStatus() == 1);
        return m;
    }
}
