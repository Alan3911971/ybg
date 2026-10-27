package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import com.ibigou.blindbox.service.PropertyWorkorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 业主群受理（/api/property/group）。
 *
 * 核心口径（与设计稿 ㉝ 业主群一致）：
 *   ① 群内说话就是提需求：AI 自动判定 → 建单 → 群里只回一条静默回执
 *   ② 过程不进群：内部判断、责任归属、报价一律不进群
 *   ③ 同题 24h 并单：同群 + 同意图 + 时间窗内已建单 → 并入原单，不重复打扰维修工
 *   ④ 判不了的主观投诉（噪音/养宠/占道/邻里纠纷）一律不落单，进「待人工判断」
 */
@Slf4j
@RestController
@RequestMapping("/api/property/group")
@RequiredArgsConstructor
public class PropertyGroupController {

    private final PropertyChatGroupRepository groupRepository;
    private final PropertyChatGroupMemberRepository memberRepository;
    private final PropertyGroupMsgRepository msgRepository;
    private final PropertyWorkorderRepository workorderRepository;
    private final PropertyWorkorderService workorderService;
    private final PropertyAdminRepository adminRepository;
    private final PropertyAdminSessionRepository sessionRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyBindRelationRepository bindRelationRepository;

    /** 复用业主登录态（与 PropertyOwnerController.TOKEN_STORE 同一份） */
    private Long validateOwner(String token) {
        if (token == null || token.isBlank()) throw new BizException("登录已过期");
        Long ownerId = PropertyOwnerController.TOKEN_STORE.get(token);
        if (ownerId == null) throw new BizException("登录已过期");
        return ownerId;
    }

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
    // 意图判定：关键词规则引擎（写死在代码里，换模型不改行为）
    // 返回 [demandKind, intentLabel, woType, confidence]
    // =====================================================================
    private static final String[][] MUTUAL_WORDS = {
            {"车位", "互助·车位"}, {"出租", "互助·车位"}, {"求租", "互助·车位"},
            {"拼车", "互助·拼车"}, {"二手", "互助·二手"}, {"师傅", "互助·找师傅"},
            {"照看", "互助·照看"}, {"代收", "互助·照看"}
    };
    private static final String[][] REPAIR_WORDS = {
            {"电梯", "报修·电梯"}, {"水管", "报修·给排水"}, {"漏水", "报修·漏水"},
            {"下水", "报修·下水道"}, {"堵", "报修·下水道"}, {"停水", "报修·停水"},
            {"停电", "报修·停电"}, {"跳闸", "报修·电路"}, {"灯", "报修·照明"},
            {"门禁", "报修·门禁"}, {"道闸", "报修·道闸"}, {"异响", "报修·异响"},
            {"坏了", "报修·公区设施"}, {"渗水", "报修·漏水"}, {"马桶", "报修·给排水"},
            {"空调", "报修·空调"}, {"暖气", "报修·供暖"}, {"消防", "报修·消防"}
    };
    private static final String[][] COMPLAINT_WORDS = {
            {"狗", "投诉·养宠"}, {"宠物", "投诉·养宠"}, {"噪音", "投诉·噪音"},
            {"吵", "投诉·噪音"}, {"装修", "投诉·装修"}, {"占道", "投诉·占道"},
            {"垃圾", "投诉·垃圾"}, {"臭", "投诉·环境"}, {"乱停", "投诉·乱停"},
            {"晾", "投诉·公共区域"}
    };
    private static final String[] BILL_WORDS = {"水费", "物业费", "电费", "多少钱", "这么贵", "账单", "缴费", "欠费"};

    private String[] classify(String content) {
        String c = content == null ? "" : content;
        for (String[] w : MUTUAL_WORDS) {
            if (c.contains(w[0])) return new String[]{"help", w[1], "3", "0.880"};
        }
        for (String[] w : REPAIR_WORDS) {
            if (c.contains(w[0])) return new String[]{"repair", w[1], "1", "0.920"};
        }
        for (String[] w : COMPLAINT_WORDS) {
            if (c.contains(w[0])) return new String[]{"complaint", w[1], "2", "0.760"};
        }
        for (String w : BILL_WORDS) {
            if (c.contains(w)) return new String[]{"bill_ask", "账单·咨询", "4", "0.810"};
        }
        if (c.contains("？") || c.contains("?") || c.contains("怎么") || c.contains("有没有")) {
            return new String[]{"help", "咨询·其它", "4", "0.550"};
        }
        return new String[]{"other", "闲聊", "0", "0.300"};
    }


    /** 业主侧 companyId 可省略：缺省取该业主第一个有效绑定 */
    private Long ownerCompany(Long ownerId, Long companyId) {
        if (companyId != null) return companyId;
        return bindRelationRepository.findFirstByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1)
                .map(PropertyBindRelation::getCompanyId)
                .orElseThrow(() -> new BizException("请先在「我的」绑定物业公司"));
    }
    // =====================================================================
    // 业主端：发消息 → 触发受理
    // =====================================================================

    @GetMapping("/owner/groups")
    public Result<List<Map<String, Object>>> myGroups(@RequestHeader("X-Owner-Token") String token,
                                                      @RequestParam(required = false) Long companyId) {
        Long ownerId = validateOwner(token);
        companyId = ownerCompany(ownerId, companyId);
        List<PropertyChatGroup> groups = groupRepository.findByCompanyIdAndStatusOrderByGroupIdAsc(companyId, 1);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PropertyChatGroup g : groups) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("groupId", g.getGroupId());
            m.put("groupName", g.getGroupName());
            m.put("groupType", g.getGroupType());
            m.put("memberCount", g.getMemberCount());
            m.put("intakeEnabled", g.getIntakeEnabled());
            Optional<PropertyChatGroupMember> me = memberRepository.findByGroupIdAndOwnerId(g.getGroupId(), ownerId);
            m.put("joined", me.isPresent() && Integer.valueOf(1).equals(me.get().getStatus()));
            m.put("myRoomNo", me.map(PropertyChatGroupMember::getRoomNo).orElse(null));
            out.add(m);
        }
        return Result.ok(out);
    }

    @PostMapping("/owner/join")
    public Result<Void> join(@RequestHeader("X-Owner-Token") String token,
                             @RequestParam Long groupId,
                             @RequestParam(required = false) String roomNo) {
        Long ownerId = validateOwner(token);
        PropertyChatGroup g = groupRepository.findById(groupId)
                .orElseThrow(() -> new BizException("群不存在"));
        PropertyChatGroupMember member = memberRepository.findByGroupIdAndOwnerId(groupId, ownerId).orElse(null);
        if (member == null) {
            member = new PropertyChatGroupMember();
            member.setGroupId(groupId);
            member.setOwnerId(ownerId);
            PropertyOwner owner = ownerRepository.findById(ownerId).orElse(null);
            member.setMemberName(owner != null ? owner.getOwnerName() : null);
            member.setMemberPhone(owner != null ? owner.getOwnerPhone() : null);
        }
        member.setRoomNo(roomNo);
        member.setMemberRole(1);
        member.setCompanyId(g.getCompanyId());
        member.setStatus(1);
        member.setJoinTime(LocalDateTime.now());
        memberRepository.save(member);
        g.setMemberCount((int) memberRepository.countByGroupIdAndStatus(groupId, 1));
        groupRepository.save(g);
        return Result.ok();
    }

    @GetMapping("/owner/msgs")
    public Result<List<Map<String, Object>>> ownerMsgs(@RequestHeader("X-Owner-Token") String token,
                                                       @RequestParam Long groupId,
                                                       @RequestParam(required = false, defaultValue = "50") Integer limit) {
        validateOwner(token);
        List<PropertyGroupMsg> list = msgRepository.findByGroupIdOrderByMsgTimeAsc(groupId);
        int from = Math.max(0, list.size() - Math.max(1, limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PropertyGroupMsg m : list.subList(from, list.size())) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("msgId", m.getMsgId());
            x.put("senderName", m.getSenderName());
            x.put("roomNo", m.getRoomNo());
            x.put("content", m.getContent());
            x.put("media", m.getMedia());
            x.put("msgTime", m.getMsgTime());
            x.put("isDemand", m.getIsDemand());
            x.put("intentLabel", m.getIntentLabel());
            x.put("woNo", m.getWoNo());
            x.put("receiptStatus", m.getReceiptStatus());
            x.put("receiptMsg", m.getReceiptMsg());
            out.add(x);
        }
        return Result.ok(out);
    }

    /**
     * 群内发言。若被判为需求 → 自动建单 + 静默回执（回执随消息一起返回，前端以 AI 身份插入一条）。
     */
    @PostMapping("/owner/send")
    public Result<Map<String, Object>> send(@RequestHeader("X-Owner-Token") String token,
                                            @RequestParam Long groupId,
                                            @RequestParam String content,
                                            @RequestParam(required = false) String media) {
        Long ownerId = validateOwner(token);
        PropertyChatGroup g = groupRepository.findById(groupId)
                .orElseThrow(() -> new BizException("群不存在"));
        if (g.getIntakeEnabled() != null && g.getIntakeEnabled() == 0) {
            throw new BizException("该群未开启 AI 受理");
        }
        PropertyOwner owner = ownerRepository.findById(ownerId).orElse(null);
        PropertyChatGroupMember me = memberRepository.findByGroupIdAndOwnerId(groupId, ownerId).orElse(null);

        PropertyGroupMsg msg = new PropertyGroupMsg();
        msg.setGroupId(groupId);
        msg.setCompanyId(g.getCompanyId());
        msg.setSenderOwnerId(ownerId);
        msg.setSenderName(owner != null ? owner.getOwnerName() : "业主");
        msg.setRoomNo(me != null ? me.getRoomNo() : null);
        msg.setContent(content);
        msg.setMedia(media);
        msg.setMsgTime(LocalDateTime.now());
        msg.setIsDemand(0);
        msg.setReceiptStatus(0);

        String[] c = classify(content);
        String kind = c[0], intent = c[1], woType = c[2];
        boolean isDemand = !"other".equals(kind);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("msgId", null);

        if (!isDemand) {
            msgRepository.save(msg);
            out.put("msgId", msg.getMsgId());
            out.put("accepted", false);
            out.put("receiptMsg", null);
            return Result.ok(out);
        }

        msg.setIsDemand(1);
        msg.setDemandKind(kind);
        msg.setIntentLabel(intent);
        msg.setConfidence(new BigDecimal(c[3]));

        // 互助类不建物业工单，挂邻里互助（由前端引导发布）
        if ("help".equals(kind) && intent.startsWith("互助")) {
            msg.setReceiptStatus(1);
            msg.setReceiptMsg("这条属于邻里互助，已放到「邻里互助」板块，邻居能直接看到");
            msg.setReceiptTime(LocalDateTime.now());
            msgRepository.save(msg);
            out.put("msgId", msg.getMsgId());
            out.put("accepted", true);
            out.put("route", "mutual");
            out.put("receiptMsg", msg.getReceiptMsg());
            return Result.ok(out);
        }

        // 主观投诉 / 账单咨询：不落单，进待人工判断
        if ("complaint".equals(kind) || "bill_ask".equals(kind)) {
            msg.setReceiptStatus(2);
            msg.setReceiptMsg("收到，这条已转人工，稍后给你结论");
            msgRepository.save(msg);
            out.put("msgId", msg.getMsgId());
            out.put("accepted", false);
            out.put("route", "manual");
            out.put("receiptMsg", "收到，这条已转人工，稍后给你结论");
            return Result.ok(out);
        }

        // 报修 / 求助：先判并单
        List<PropertyGroupMsg> mergeable = msgRepository.findMergeable(groupId, intent, LocalDateTime.now().minusHours(24));
        if (!mergeable.isEmpty()) {
            PropertyGroupMsg origin = mergeable.get(0);
            msg.setWoId(origin.getWoId());
            msg.setWoNo(origin.getWoNo());
            msg.setReceiptStatus(4);
            msg.setReceiptMsg("已并入 " + origin.getWoNo() + "（同楼栋同现象），不用重复报");
            msg.setReceiptTime(LocalDateTime.now());
            msgRepository.save(msg);
            out.put("msgId", msg.getMsgId());
            out.put("accepted", true);
            out.put("route", "merged");
            out.put("woNo", origin.getWoNo());
            out.put("receiptMsg", msg.getReceiptMsg());
            return Result.ok(out);
        }

        // 建单
        String title = "群内报修·" + abbreviate(content, 40);
        PropertyWorkorder wo = workorderService.createWorkorder(ownerId, g.getCompanyId(),
                Integer.parseInt(woType), title, content, media, 1, intent);
        wo.setSource("group");
        wo.setSourceRef(String.valueOf(msg.getMsgId() == null ? "" : msg.getMsgId()));
        workorderRepository.save(wo);

        String eta = "1".equals(woType) ? "2 小时" : "24 小时";
        msg.setWoId(wo.getWoId());
        msg.setWoNo(wo.getWoNo());
        msg.setReceiptStatus(1);
        msg.setReceiptMsg("已受理 · 工单 " + wo.getWoNo() + " · " + eta + "内响应，进度我私信你");
        msg.setReceiptTime(LocalDateTime.now());
        msgRepository.save(msg);

        // sourceRef 补真实消息ID
        wo.setSourceRef(String.valueOf(msg.getMsgId()));
        workorderRepository.save(wo);

        out.put("msgId", msg.getMsgId());
        out.put("accepted", true);
        out.put("route", "workorder");
        out.put("woNo", wo.getWoNo());
        out.put("receiptMsg", msg.getReceiptMsg());
        return Result.ok(out);
    }

    // =====================================================================
    // 后台：受理台
    // =====================================================================

    @GetMapping("/admin/summary")
    public Result<Map<String, Object>> adminSummary(@RequestHeader("X-Property-Token") String token,
                                                    @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        LocalDateTime dayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long demands = msgRepository.countByCompanyIdAndIsDemandAndMsgTimeAfter(companyId, 1, dayStart);
        long pending = msgRepository.countByCompanyIdAndReceiptStatusAndMsgTimeAfter(companyId, 2, dayStart);
        long accepted = msgRepository.countByCompanyIdAndReceiptStatusAndMsgTimeAfter(companyId, 1, dayStart);
        long merged = msgRepository.countByCompanyIdAndReceiptStatusAndMsgTimeAfter(companyId, 4, dayStart);
        long members = memberRepository.countByCompanyId(companyId);
        long groups = groupRepository.countByCompanyIdAndStatus(companyId, 1);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groups", groups);
        m.put("members", members);
        m.put("todayDemand", demands);
        m.put("accepted", accepted);
        m.put("merged", merged);
        m.put("pending", pending);
        return Result.ok(m);
    }

    @GetMapping("/admin/msgs")
    public Result<List<Map<String, Object>>> adminMsgs(@RequestHeader("X-Property-Token") String token,
                                                       @RequestParam(required = false) Long companyId,
                                                       @RequestParam(required = false) Integer receiptStatus,
                                                       @RequestParam(required = false) Integer isDemand,
                                                       @RequestParam(required = false, defaultValue = "100") Integer limit) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        List<PropertyGroupMsg> list;
        if (receiptStatus != null) {
            list = msgRepository.findByCompanyIdAndReceiptStatusOrderByMsgTimeDesc(companyId, receiptStatus);
        } else {
            list = msgRepository.findByCompanyIdOrderByMsgTimeDesc(companyId);
        }
        if (isDemand != null) {
            list = list.stream().filter(x -> isDemand.equals(x.getIsDemand())).collect(Collectors.toList());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int n = 0;
        for (PropertyGroupMsg m : list) {
            if (n++ >= limit) break;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("msgId", m.getMsgId());
            x.put("groupId", m.getGroupId());
            x.put("senderName", m.getSenderName());
            x.put("roomNo", m.getRoomNo());
            x.put("content", m.getContent());
            x.put("media", m.getMedia());
            x.put("msgTime", m.getMsgTime());
            x.put("isDemand", m.getIsDemand());
            x.put("demandKind", m.getDemandKind());
            x.put("intentLabel", m.getIntentLabel());
            x.put("confidence", m.getConfidence());
            x.put("woId", m.getWoId());
            x.put("woNo", m.getWoNo());
            x.put("receiptStatus", m.getReceiptStatus());
            x.put("receiptMsg", m.getReceiptMsg());
            x.put("handleNote", m.getHandleNote());
            out.add(x);
        }
        return Result.ok(out);
    }

    /**
     * 人工判断：action = create(建单) / ignore(不建单) / merge(并入指定单)
     */
    @PostMapping("/admin/msgs/{msgId}/decide")
    public Result<Map<String, Object>> decide(@RequestHeader("X-Property-Token") String token,
                                              @PathVariable Long msgId,
                                              @RequestParam String action,
                                              @RequestParam(required = false) String note,
                                              @RequestParam(required = false) Long mergeIntoWoId) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyGroupMsg m = msgRepository.findById(msgId)
                .orElseThrow(() -> new BizException("消息不存在"));
        Map<String, Object> out = new LinkedHashMap<>();
        m.setHandleAdminId(admin.getAdminId());
        m.setHandleTime(LocalDateTime.now());
        m.setHandleNote(note);

        if ("create".equals(action)) {
            if (m.getWoId() != null) throw new BizException("该条已有关联工单");
            String title = "群内投诉·" + abbreviate(m.getContent(), 40);
            PropertyWorkorder wo = workorderService.createWorkorder(m.getSenderOwnerId(), m.getCompanyId(),
                    2, title, m.getContent(), m.getMedia(), 1, m.getIntentLabel());
            wo.setSource("group");
            workorderRepository.save(wo);
            m.setWoId(wo.getWoId());
            m.setWoNo(wo.getWoNo());
            m.setReceiptStatus(1);
            m.setReceiptMsg("已受理 · 工单 " + wo.getWoNo() + "，进度我私信你");
            m.setReceiptTime(LocalDateTime.now());
            wo.setSourceRef(String.valueOf(m.getMsgId()));
            workorderRepository.save(wo);
            out.put("woNo", wo.getWoNo());
        } else if ("ignore".equals(action)) {
            m.setReceiptStatus(3);
        } else if ("merge".equals(action)) {
            if (mergeIntoWoId == null) throw new BizException("请指定要并入的工单");
            PropertyWorkorder wo = workorderRepository.findById(mergeIntoWoId)
                    .orElseThrow(() -> new BizException("工单不存在"));
            m.setWoId(wo.getWoId());
            m.setWoNo(wo.getWoNo());
            m.setReceiptStatus(4);
            m.setReceiptMsg("已并入 " + wo.getWoNo() + "（同楼栋同现象），不用重复报");
            m.setReceiptTime(LocalDateTime.now());
            out.put("woNo", wo.getWoNo());
        } else {
            throw new BizException("未知操作：" + action);
        }
        msgRepository.save(m);
        out.put("msgId", m.getMsgId());
        out.put("receiptStatus", m.getReceiptStatus());
        out.put("receiptMsg", m.getReceiptMsg());
        return Result.ok(out);
    }

    /** 建群（后台） */
    @PostMapping("/admin/groups")
    public Result<PropertyChatGroup> createGroup(@RequestHeader("X-Property-Token") String token,
                                                 @RequestParam(required = false) Long companyId,
                                                 @RequestParam String groupName,
                                                 @RequestParam(required = false) Long communityId,
                                                 @RequestParam(required = false) Integer groupType) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        PropertyChatGroup g = new PropertyChatGroup();
        g.setCompanyId(companyId);
        g.setCommunityId(communityId);
        g.setGroupName(groupName);
        g.setGroupType(groupType == null ? 1 : groupType);
        g.setMemberCount(0);
        g.setIntakeEnabled(1);
        g.setStatus(1);
        return Result.ok(groupRepository.save(g));
    }

    private static String abbreviate(String s, int n) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }
}
