package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商家邀请与两段式佣金（/api/property/referral）。
 *
 * 链路：业主跟老板谈妥 → 手机端出专属邀请码（72h·单类目）→ 老板扫码自注册（执照 OCR）
 *      → 物业过审 10 分钟 → ¥30 秒进业主物业钱包 → 首单满 ¥100 且 7 天无退款再补 ¥70
 *
 * 为什么两段式：敞口只 ¥30（一人一码·单类目·72h），刷 10 个假商家只赚 ¥300
 * 但要过 10 次执照 OCR 与人工过审，不划算；¥70 绑首单与退货期，堵住「注册完就退店」。
 * 商家侧不新做：注册成功直接拿到存量商家后台账号。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/referral")
@RequiredArgsConstructor
public class PropertyReferralController {

    private final PropertyMerchantReferralRepository referralRepository;
    private final MerchantPropertyBindingRepository bindingRepository;
    private final PropertyWalletRepository walletRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyBindRelationRepository bindRelationRepository;
    private final PropertyRoomRepository roomRepository;
    private final PropertyAdminRepository adminRepository;
    private final PropertyAdminSessionRepository sessionRepository;

    public static final BigDecimal REWARD_30 = new BigDecimal("30.00");
    public static final BigDecimal REWARD_70 = new BigDecimal("70.00");
    public static final BigDecimal FIRST_ORDER_THRESHOLD = new BigDecimal("100.00");
    private static final int EXPIRE_HOURS = 72;

    private Long validateOwner(String token) {
        Long ownerId = PropertyOwnerController.TOKEN_STORE.get(token == null ? "" : token);
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


    /** 业主侧 companyId 可省略：缺省取该业主第一个有效绑定 */
    private Long ownerCompany(Long ownerId, Long companyId) {
        if (companyId != null) return companyId;
        return bindRelationRepository.findFirstByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1)
                .map(PropertyBindRelation::getCompanyId)
                .orElseThrow(() -> new BizException("请先在「我的」绑定物业公司"));
    }
    // ================== 业主端 ==================

    /** 出码：一人一码，24h 内已有活跃码直接返回原码（不重复出） */
    @PostMapping("/owner/codes")
    public Result<Map<String, Object>> createCode(@RequestHeader("X-Owner-Token") String token,
                                                  @RequestParam(required = false) Long companyId,
                                                  @RequestParam(required = false) String category) {
        Long ownerId = validateOwner(token);
        companyId = ownerCompany(ownerId, companyId);
        LocalDateTime now = LocalDateTime.now();

        List<PropertyMerchantReferral> active = referralRepository
                .findByOwnerIdAndStatusInAndExpireTimeAfter(ownerId, Arrays.asList(0, 1, 2), now);
        if (!active.isEmpty()) {
            PropertyMerchantReferral exist = active.get(0);
            Map<String, Object> m = toMap(exist);
            m.put("reused", true);
            m.put("tip", "你还有一个 72 小时内有效的邀请码，先把它用掉");
            return Result.ok(m);
        }

        String roomNo = roomNoOf(ownerId, companyId);
        String seq = String.format("%03d", (referralRepository.count() % 999) + 1);
        String code = "SH-" + (roomNo == null ? ownerId : roomNo.replaceAll("[^0-9A-Za-z]", "-")) + "-" + seq;

        PropertyMerchantReferral r = new PropertyMerchantReferral();
        r.setCompanyId(companyId);
        r.setCode(code);
        r.setOwnerId(ownerId);
        r.setRoomNo(roomNo);
        r.setCategory(category);
        r.setStatus(0);
        r.setExpireTime(now.plusHours(EXPIRE_HOURS));
        r.setReward30Status(0);
        r.setReward70Status(0);
        referralRepository.save(r);

        Map<String, Object> m = toMap(r);
        m.put("reused", false);
        m.put("registerUrl", "/property/customer/merchant-register.html?code=" + code);
        return Result.ok(m);
    }

    @GetMapping("/owner/mine")
    public Result<Map<String, Object>> mine(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwner(token);
        List<PropertyMerchantReferral> list = referralRepository.findAll().stream()
                .filter(r -> ownerId.equals(r.getOwnerId()))
                .sorted(Comparator.comparing(PropertyMerchantReferral::getCreateTime).reversed())
                .collect(Collectors.toList());
        BigDecimal total = list.stream()
                .map(r -> (Integer.valueOf(1).equals(r.getReward30Status()) ? REWARD_30 : BigDecimal.ZERO)
                        .add(Integer.valueOf(1).equals(r.getReward70Status()) ? REWARD_70 : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list.stream().map(this::toMap).collect(Collectors.toList()));
        out.put("totalReward", total);
        return Result.ok(out);
    }

    // ================== 公开：扫码校验与登记 ==================

    /** 商家扫码后先校验码是否可用（免登录） */
    @GetMapping("/public/validate")
    public Result<Map<String, Object>> validate(@RequestParam String code) {
        PropertyMerchantReferral r = referralRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BizException("邀请码不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        boolean expired = r.getStatus() != null && r.getStatus() == 3
                || (r.getExpireTime() != null && r.getExpireTime().isBefore(LocalDateTime.now()));
        boolean used = r.getStatus() != null && (r.getStatus() == 2 || r.getStatus() == 4);
        m.put("code", r.getCode());
        m.put("valid", !expired && !used);
        m.put("expired", expired);
        m.put("used", used);
        m.put("category", r.getCategory());
        m.put("companyId", r.getCompanyId());
        m.put("expireTime", r.getExpireTime());
        m.put("inviterRoomNo", r.getRoomNo());
        if (expired && r.getStatus() != null && r.getStatus() == 0) {
            r.setStatus(3);
            referralRepository.save(r);
        }
        return Result.ok(m);
    }

    /** 商家扫码进入注册页时登记「已扫码」 */
    @PostMapping("/public/scan")
    public Result<Void> scan(@RequestParam String code) {
        PropertyMerchantReferral r = referralRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BizException("邀请码不存在"));
        if (r.getStatus() != null && r.getStatus() == 0) {
            r.setStatus(1);
            r.setScanTime(LocalDateTime.now());
            referralRepository.save(r);
        }
        return Result.ok();
    }

    // ================== 后台 ==================

    @GetMapping("/admin/summary")
    public Result<Map<String, Object>> summary(@RequestHeader("X-Property-Token") String token,
                                               @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        LocalDateTime monthStart = LocalDateTime.now().toLocalDate().withDayOfMonth(1).atStartOfDay();
        List<PropertyMerchantReferral> month = referralRepository.findByCompanyIdOrderByCreateTimeDesc(companyId)
                .stream().filter(r -> r.getCreateTime() != null && r.getCreateTime().isAfter(monthStart))
                .collect(Collectors.toList());
        long audited = month.stream().filter(r -> Integer.valueOf(2).equals(r.getStatus())).count();
        long r30 = month.stream().filter(r -> Integer.valueOf(1).equals(r.getReward30Status())).count();
        long r70Pending = month.stream().filter(r -> Integer.valueOf(2).equals(r.getStatus())
                && Integer.valueOf(0).equals(r.getReward70Status())).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("monthInvite", month.size());
        m.put("audited", audited);
        m.put("reward30Count", r30);
        m.put("reward30Amount", REWARD_30.multiply(new BigDecimal(r30)));
        m.put("reward70Pending", r70Pending);
        return Result.ok(m);
    }

    @GetMapping("/admin/list")
    public Result<List<Map<String, Object>>> adminList(@RequestHeader("X-Property-Token") String token,
                                                       @RequestParam(required = false) Long companyId,
                                                       @RequestParam(required = false) Integer status) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        List<PropertyMerchantReferral> list = status != null
                ? referralRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(companyId, status)
                : referralRepository.findByCompanyIdOrderByCreateTimeDesc(companyId);
        return Result.ok(list.stream().map(this::toMap).collect(Collectors.toList()));
    }

    /**
     * 过审：通过则建商家-物业分账绑定 + ¥30 立即入邀请人物业钱包。
     */
    @PostMapping("/admin/{referralId}/audit")
    public Result<Map<String, Object>> audit(@RequestHeader("X-Property-Token") String token,
                                             @PathVariable Long referralId,
                                             @RequestParam boolean pass,
                                             @RequestParam(required = false) String merchantNo,
                                             @RequestParam(required = false) String merchantName,
                                             @RequestParam(required = false) String note) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyMerchantReferral r = referralRepository.findById(referralId)
                .orElseThrow(() -> new BizException("邀请记录不存在"));
        if (r.getStatus() != null && r.getStatus() == 2) throw new BizException("该邀请已过审");
        r.setAuditAdminId(admin.getAdminId());
        r.setAuditTime(LocalDateTime.now());
        r.setAuditNote(note);

        if (!pass) {
            r.setStatus(4);
            referralRepository.save(r);
            return Result.ok(toMap(r));
        }
        if (merchantNo == null || merchantNo.isBlank()) {
            throw new BizException("过审必须填商家编号（商家注册时生成）");
        }
        r.setStatus(2);
        r.setMerchantNo(merchantNo);
        r.setMerchantName(merchantName);

        MerchantPropertyBinding b = new MerchantPropertyBinding();
        b.setMerchantNo(merchantNo);
        b.setCompanyId(r.getCompanyId());
        b.setSplitMode(1);
        b.setStatus(1);
        b.setRemark("业主邀请入驻 · 邀请码 " + r.getCode());
        b.setReferrerOwnerId(r.getOwnerId());
        b.setInviteCode(r.getCode());
        b.setInviteExpireAt(r.getExpireTime());
        b.setReferralRewardStatus("R30");
        b.setCreateTime(LocalDateTime.now());
        b.setUpdateTime(LocalDateTime.now());
        bindingRepository.save(b);
        r.setBindingId(b.getId());

        // ¥30 立即到账
        if (!Integer.valueOf(1).equals(r.getReward30Status())) {
            creditWallet(r.getOwnerId(), r.getCompanyId(), REWARD_30,
                    "商家邀请奖励 ¥30 · " + (merchantName == null ? merchantNo : merchantName));
            r.setReward30Status(1);
            r.setReward30Time(LocalDateTime.now());
        }
        referralRepository.save(r);
        return Result.ok(toMap(r));
    }

    /** 商家首单回写：满 ¥100 则开始 7 天观察期 */
    @PostMapping("/admin/{referralId}/first-order")
    public Result<Map<String, Object>> firstOrder(@RequestHeader("X-Property-Token") String token,
                                                  @PathVariable Long referralId,
                                                  @RequestParam BigDecimal amount) {
        validateAdmin(token);
        PropertyMerchantReferral r = referralRepository.findById(referralId)
                .orElseThrow(() -> new BizException("邀请记录不存在"));
        r.setFirstOrderAmount(amount);
        r.setFirstOrderTime(LocalDateTime.now());
        if (amount.compareTo(FIRST_ORDER_THRESHOLD) >= 0) {
            r.setRefundDeadline(LocalDateTime.now().plusDays(7));
            r.setReward70Status(0);
        } else {
            r.setReward70Status(2);
        }
        referralRepository.save(r);
        Map<String, Object> m = toMap(r);
        m.put("tip", amount.compareTo(FIRST_ORDER_THRESHOLD) >= 0
                ? "首单满 ¥100：7 天无退款后发放 ¥70"
                : "首单未满 ¥100：不触发 ¥70");
        return Result.ok(m);
    }

    /** 发放 ¥70（需满足：已过审 + 首单满 ¥100 + 观察期已过） */
    @PostMapping("/admin/{referralId}/settle70")
    public Result<Map<String, Object>> settle70(@RequestHeader("X-Property-Token") String token,
                                                @PathVariable Long referralId) {
        validateAdmin(token);
        PropertyMerchantReferral r = referralRepository.findById(referralId)
                .orElseThrow(() -> new BizException("邀请记录不存在"));
        if (!Integer.valueOf(2).equals(r.getStatus())) throw new BizException("尚未过审，不能发 ¥70");
        if (Integer.valueOf(1).equals(r.getReward70Status())) throw new BizException("¥70 已发放");
        if (r.getFirstOrderAmount() == null || r.getFirstOrderAmount().compareTo(FIRST_ORDER_THRESHOLD) < 0) {
            throw new BizException("首单未满 ¥100，不满足条件");
        }
        if (r.getRefundDeadline() != null && r.getRefundDeadline().isAfter(LocalDateTime.now())) {
            throw new BizException("7 天退款观察期未满，到期日 " + r.getRefundDeadline());
        }
        creditWallet(r.getOwnerId(), r.getCompanyId(), REWARD_70, "商家邀请奖励 ¥70 · 首单达成");
        r.setReward70Status(1);
        r.setReward70Time(LocalDateTime.now());
        referralRepository.save(r);
        return Result.ok(toMap(r));
    }

    // ================== 工具 ==================

    private void creditWallet(Long ownerId, Long companyId, BigDecimal amount, String reason) {
        PropertyWallet w = walletRepository.findByOwnerIdAndCompanyId(ownerId, companyId).orElse(null);
        if (w == null) {
            w = new PropertyWallet();
            w.setOwnerId(ownerId);
            w.setCompanyId(companyId);
            w.setBalance(BigDecimal.ZERO);
            w.setCreateTime(LocalDateTime.now());
            w.setUpdateTime(LocalDateTime.now());
        }
        w.setBalance(w.getBalance() == null ? amount : w.getBalance().add(amount));
        w.setUpdateTime(LocalDateTime.now());
        walletRepository.save(w);
        log.info("Referral reward {} -> owner {} ({})", amount, ownerId, reason);
    }

    private String roomNoOf(Long ownerId, Long companyId) {
        return bindRelationRepository.findFirstByOwnerIdAndCompanyIdAndStatusOrderByBindTimeAsc(ownerId, companyId, 1)
                .map(PropertyBindRelation::getRoomId)
                .flatMap(roomRepository::findById)
                .map(PropertyRoom::getRoomNo)
                .orElse(null);
    }

    private Map<String, Object> toMap(PropertyMerchantReferral r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("referralId", r.getReferralId());
        m.put("code", r.getCode());
        m.put("ownerId", r.getOwnerId());
        m.put("roomNo", r.getRoomNo());
        m.put("category", r.getCategory());
        m.put("status", r.getStatus());
        m.put("expireTime", r.getExpireTime());
        m.put("merchantNo", r.getMerchantNo());
        m.put("merchantName", r.getMerchantName());
        m.put("scanTime", r.getScanTime());
        m.put("auditTime", r.getAuditTime());
        m.put("reward30Status", r.getReward30Status());
        m.put("reward30Time", r.getReward30Time());
        m.put("reward70Status", r.getReward70Status());
        m.put("reward70Time", r.getReward70Time());
        m.put("firstOrderAmount", r.getFirstOrderAmount());
        m.put("refundDeadline", r.getRefundDeadline());
        m.put("createTime", r.getCreateTime());
        return m;
    }
}
