package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.AnnounceLog;
import com.ibigou.blindbox.entity.MemberOrder;
import com.ibigou.blindbox.entity.MerchantMessage;
import com.ibigou.blindbox.entity.OfflineOrder;
import com.ibigou.blindbox.entity.UserCoupon;
import com.ibigou.blindbox.service.*;
import com.ibigou.blindbox.entity.Merchant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商家 H5 后台 API（B 端）：登录、手工核销/扣减、配置只读、用户资产查询。
 * 鉴权：X-Merchant-Token（登录后获取）。
 */
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantAuthService authService;
    private final ManualService manualService;
    private final GlobalConfigService configService;
    private final CouponService couponService;
    private final BalanceService balanceService;
    private final WalletService walletService;
    private final MemberService memberService;
    private final OfflineOrderService offlineOrderService;
    private final WxPayService wxPayService;
    private final MessageService messageService;
    private final DynamicQrService dynamicQrService;
    private final AnnounceService announceService;
    private final IdentityQrService identityQrService;
    private final ReportService reportService;
    private final com.ibigou.blindbox.repository.MerchantRepository merchantRepository;
    private final com.ibigou.blindbox.repository.IbigouOrderRepository ibigouOrderRepository;
    private final com.ibigou.blindbox.repository.UserCouponRepository userCouponRepository;
    private final com.ibigou.blindbox.repository.BoxPrizePoolRepository boxPrizePoolRepository;
    private final com.ibigou.blindbox.repository.OfflineOrderRepository offlineOrderRepository;
    private final com.ibigou.blindbox.repository.MemberProfileRepository memberProfileRepository;
    private final com.ibigou.blindbox.repository.MemberVisitRepository memberVisitRepository;
    private final com.ibigou.blindbox.repository.MemberAppointmentRepository memberAppointmentRepository;
    private final com.ibigou.blindbox.repository.MemberGiftRepository memberGiftRepository;
    private final com.ibigou.blindbox.repository.MerchantMessageRepository merchantMessageRepository;
    private final ChatService chatService;

    @PostMapping("/auth/login")
    public Result<String> login(@RequestParam String account, @RequestParam String password) {
        return Result.ok(authService.login(account, password));
    }

    /** F-1 手工核销优惠券（仅发行商家；外来券禁止） */
    @PostMapping("/coupon/verify-manual")
    public Result<UserCoupon> verifyManual(@RequestHeader("X-Merchant-Token") String token,
                                           @RequestParam Long couponId) {
        String operator = authService.merchantNoByToken(token);
        return Result.ok(manualService.manualVerifyCoupon(couponId, operator));
    }

    /** F-2 兜底完成订单（V1.5：系统自动四重 min 计算抵扣，商家仅确认） */
    @PostMapping("/balance/manual-deduct")
    public Result<Void> manualDeduct(@RequestHeader("X-Merchant-Token") String token,
                                     @RequestParam String userPhone,
                                     @RequestParam BigDecimal orderAmount) {
        String operator = authService.merchantNoByToken(token);
        manualService.manualCompleteOrder(userPhone, operator, orderAmount);
        return Result.ok();
    }

    /** 平台参数只读（商家不可修改；含跨店返还比例） */
    @GetMapping("/config")
    public Result<MerchantConfig> config(@RequestHeader("X-Merchant-Token") String token) {
        authService.merchantNoByToken(token);
        return Result.ok(new MerchantConfig(configService.balanceDeductRate(), configService.ibigouChannelOpen(),
                Integer.parseInt(configService.get("cross_store_return_percent", "5"))));
    }

    /** 设置当前设备为播报设备 */
    @PostMapping("/config/set-broadcast-device")
    public Result<Void> setBroadcastDevice(@RequestHeader("X-Merchant-Token") String token) {
        String mno = authService.merchantNoByToken(token);
        Merchant m = merchantRepository.findById(mno).orElse(null);
        if (m != null) {
            m.setBroadcastToken(token);
            m.setUpdateTime(java.time.LocalDateTime.now());
            merchantRepository.save(m);
        }
        return Result.ok();
    }

    /** 取消播报设备（所有设备都播报） */
    @PostMapping("/config/clear-broadcast-device")
    public Result<Void> clearBroadcastDevice(@RequestHeader("X-Merchant-Token") String token) {
        String mno = authService.merchantNoByToken(token);
        Merchant m = merchantRepository.findById(mno).orElse(null);
        if (m != null) {
            m.setBroadcastToken(null);
            m.setUpdateTime(java.time.LocalDateTime.now());
            merchantRepository.save(m);
        }
        return Result.ok();
    }

    /** 用户可用券（线下本店 + 宜必购）与余额查询 */
    @GetMapping("/user/{userPhone}/assets")
    public Result<UserAssets> userAssets(@RequestHeader("X-Merchant-Token") String token,
                                         @PathVariable String userPhone) {
        authService.merchantNoByToken(token);
        return Result.ok(new UserAssets(
                couponService.availableForOffline(userPhone, authService.merchantNoByToken(token)),
                couponService.availableForIbigou(userPhone),
                balanceService.availableBalance(userPhone)));
    }

    public record MerchantConfig(int balanceDeductRate, boolean ibigouChannelOpen, int crossStoreReturnPercent) {
    }

    // ---------------- 动态核销码核验（V1.5 P2） ----------------

    /** 商家扫用户动态核销码：校验并返回用户本店资产（券/余额），供兜底核销 */
    @GetMapping("/qr/verify")
    public Result<QrVerifyResult> qrVerify(@RequestHeader("X-Merchant-Token") String token,
                                           @RequestParam String qrToken) {
        String merchantNo = authService.merchantNoByToken(token);
        String userPhone = dynamicQrService.verify(qrToken);
        return Result.ok(new QrVerifyResult(userPhone,
                couponService.availableForOffline(userPhone, merchantNo),
                balanceService.availableBalance(userPhone)));
    }

    /** 动态码核验结果 */
    public record QrVerifyResult(String userPhone,
                                 java.util.List<UserCoupon> coupons, java.math.BigDecimal balance) {
    }

    // ---------------- 长期身份二维码核验（P1：退款三方式之一主推） ----------------

    /** 商家扫用户长期身份码：验签 → 用户本店历史订单（退款定位） */
    @GetMapping("/identity/verify")
    public Result<IdentityVerifyResult> identityVerify(@RequestHeader("X-Merchant-Token") String token,
                                                       @RequestParam String content) {
        String merchantNo = authService.merchantNoByToken(token);
        String userPhone = identityQrService.verify(content);
        return Result.ok(new IdentityVerifyResult(userPhone,
                offlineOrderService.listOrders(merchantNo, userPhone, null)));
    }

    public record IdentityVerifyResult(String userPhone, java.util.List<OfflineOrder> orders) {
    }

    // ---------------- 站内消息（V1.5 P2） ----------------

    @GetMapping("/messages")
    public Result<java.util.List<MerchantMessage>> messages(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(messageService.list(authService.merchantNoByToken(token)));
    }

    @GetMapping("/messages/unread-count")
    public Result<Long> unreadCount(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(messageService.unreadCount(authService.merchantNoByToken(token)));
    }

    @PostMapping("/messages/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, @RequestHeader("X-Merchant-Token") String token) {
        messageService.markRead(id, authService.merchantNoByToken(token));
        return Result.ok();
    }

    @PostMapping("/messages/read-all")
    public Result<Void> markAllRead(@RequestHeader("X-Merchant-Token") String token) {
        messageService.markAllRead(authService.merchantNoByToken(token));
        return Result.ok();
    }

    // ---------------- 播报事件（V1.5 P2 语音/音箱） ----------------

    /** 增量轮询新播报事件（商家 H5 定时拉取朗读） */
    @GetMapping("/announce/poll")
    public Result<java.util.Map<String, Object>> announcePoll(@RequestHeader("X-Merchant-Token") String token,
                                                            @RequestParam(required = false) Long afterId) {
        String mno = authService.merchantNoByToken(token);
        Merchant m = merchantRepository.findById(mno).orElse(null);
        boolean isBroadcast = m == null || m.getBroadcastToken() == null || m.getBroadcastToken().equals(token);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("events", announceService.poll(mno, afterId));
        result.put("isBroadcastDevice", isBroadcast);
        return Result.ok(result);
    }

    /** 全部播报记录（新→旧） */
    @GetMapping("/announce/list")
    public Result<java.util.List<AnnounceLog>> announceList(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(announceService.list(authService.merchantNoByToken(token)));
    }

    // ---------------- 本店订单列表 / 交易流水号（V1.5 P2） ----------------

    /** 本店全部订单（支持按用户手机号/日期筛选，用于对账与退款定位） */
    @GetMapping("/orders")
    public Result<java.util.List<OfflineOrder>> orders(@RequestHeader("X-Merchant-Token") String token,
                                                       @RequestParam(required = false) String userPhone,
                                                       @RequestParam(required = false) String date) {
        String merchantNo = authService.merchantNoByToken(token);
        return Result.ok(offlineOrderService.listOrders(merchantNo, userPhone, date));
    }

    /** 商家端：会员管理——聚合本店消费顾客（宜必购+线下），按最近消费倒序 */
    @GetMapping("/members")
    public Result<java.util.List<java.util.Map<String, Object>>> members(@RequestHeader("X-Merchant-Token") String token) {
        String merchantNo = authService.merchantNoByToken(token);
        java.util.Map<String, java.util.Map<String, Object>> agg = new java.util.LinkedHashMap<>();
        java.util.List<com.ibigou.blindbox.entity.IbigouOrder> ib = ibigouOrderRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo);
        java.util.List<com.ibigou.blindbox.entity.OfflineOrder> of = offlineOrderRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo);
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (var o : ib) rows.add(new Object[]{o.getUserPhone(), o.getPayAmount(),
                o.getOrderAmount().subtract(o.getPayAmount()).max(BigDecimal.ZERO), o.getCreateTime(), o.getCouponId()});
        for (var o : of) rows.add(new Object[]{o.getUserPhone(), o.getPayAmount(),
                o.getOrderAmount().subtract(o.getPayAmount()).max(BigDecimal.ZERO), o.getCreateTime(), o.getCouponId()});
        for (Object[] r : rows) {
            String phone = (String) r[0];
            if (phone == null || phone.isEmpty()) continue;
            var m = agg.computeIfAbsent(phone, k -> {
                var mm = new java.util.HashMap<String, Object>();
                mm.put("userPhone", k);
                mm.put("orderCount", 0);
                mm.put("totalAmount", BigDecimal.ZERO);
                mm.put("totalSave", BigDecimal.ZERO);
                mm.put("lastTs", null);
                mm.put("lastPrize", "");
                mm.put("lastPrizeEmoji", "🎁");
                var prof = memberProfileRepository.findByMerchantNoAndUserPhone(merchantNo, k).orElse(null);
                mm.put("name", prof == null ? null : prof.getName());
                mm.put("gender", prof == null ? null : prof.getGender());
                return mm;
            });
            m.put("orderCount", (Integer) m.get("orderCount") + 1);
            m.put("totalAmount", ((BigDecimal) m.get("totalAmount")).add((BigDecimal) r[1]));
            m.put("totalSave", ((BigDecimal) m.get("totalSave")).add((BigDecimal) r[2]));
            java.time.LocalDateTime ts = (java.time.LocalDateTime) r[3];
            if (m.get("lastTs") == null || ((java.time.LocalDateTime) m.get("lastTs")).isBefore(ts)) {
                m.put("lastTs", ts);
                // 最近一笔的奖品
                String pn = "";
                String pe = "🎁";
                Long cid = (Long) r[4];
                if (cid != null) {
                    var uc = userCouponRepository.findById(cid);
                    if (uc.isPresent()) {
                        var coupon = uc.get();
                        String src = coupon.getSourceMerchantNo() == null ? merchantNo : coupon.getSourceMerchantNo();
                        var pools = boxPrizePoolRepository.findByMerchantNoAndPrizeTypeAndPrizeValue(
                                src, coupon.getPrizeType(), coupon.getPrizeValue());
                        for (var p : pools) { if (p.getRemark() != null && !p.getRemark().isEmpty()) { pn = p.getRemark(); break; } }
                        int pt = coupon.getPrizeType() == null ? 1 : coupon.getPrizeType();
                        String[] emojis = {"", "🎟️", "💵", "💰", "👑"};
                        String[] tns = {"", "折扣券", "立减券", "余额券", "免单券"};
                        if (pt >= 1 && pt <= 4) { pe = emojis[pt]; if (pn.isEmpty()) pn = tns[pt]; }
                    }
                }
                m.put("lastPrize", pn);
                m.put("lastPrizeEmoji", pe);
            }
        }
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>(agg.values());
        result.sort((a, b) -> {
            Object ta = a.get("lastTs"); Object tb = b.get("lastTs");
            if (ta == null) return 1;
            if (tb == null) return -1;
            return ((java.time.LocalDateTime) tb).compareTo((java.time.LocalDateTime) ta);
        });
        return Result.ok(result);
    }

    /** 商家端：会员详情（资料+回访+预约+本店订单） */
    @GetMapping("/members/{phone}/detail")
    public Result<java.util.Map<String, Object>> memberDetail(@RequestHeader("X-Merchant-Token") String token,
                                                             @PathVariable String phone) {
        String merchantNo = authService.merchantNoByToken(token);
        java.util.Map<String, Object> r = new java.util.HashMap<>();
        // 资料
        var prof = memberProfileRepository.findByMerchantNoAndUserPhone(merchantNo, phone);
        r.put("profile", prof.map(p -> {
            var m = new java.util.HashMap<String, Object>();
            m.put("name", p.getName());
            m.put("gender", p.getGender());
            m.put("customerPref", p.getCustomerPref());
            m.put("familyPref", p.getFamilyPref());
            m.put("birthday", p.getBirthday());
            m.put("remark", p.getRemark());
            m.put("editor", p.getEditor());
            m.put("updateTime", p.getUpdateTime());
            return m;
        }).orElse(null));
        // 回访
        java.util.List<java.util.Map<String, Object>> visits = new java.util.ArrayList<>();
        for (var v : memberVisitRepository.findByMerchantNoAndUserPhoneOrderByCreateTimeDesc(merchantNo, phone)) {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", v.getId());
            m.put("content", v.getContent());
            m.put("result", v.getResult());
            m.put("visitTime", v.getVisitTime());
            m.put("createTime", v.getCreateTime());
            visits.add(m);
        }
        r.put("visits", visits);
        // 预约
        java.util.List<java.util.Map<String, Object>> appts = new java.util.ArrayList<>();
        for (var a : memberAppointmentRepository.findByMerchantNoAndUserPhoneOrderByApptTimeDesc(merchantNo, phone)) {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", a.getId());
            m.put("apptTime", a.getApptTime());
            m.put("serviceItem", a.getServiceItem());
            m.put("remark", a.getRemark());
            m.put("status", a.getStatus());
            m.put("remindMinutes", a.getRemindMinutes());
            appts.add(m);
        }
        r.put("appointments", appts);
        // 赠送记录
        java.util.List<java.util.Map<String, Object>> gifts = new java.util.ArrayList<>();
        for (var g : memberGiftRepository.findByMerchantNoAndUserPhoneOrderByCreateTimeDesc(merchantNo, phone)) {
            var m = new java.util.HashMap<String, Object>();
            m.put("id", g.getId());
            m.put("goodsId", g.getGoodsId());
            m.put("goodsName", g.getGoodsName());
            m.put("quantity", g.getQuantity());
            m.put("remark", g.getRemark());
            m.put("source", g.getSource());
            m.put("createTime", g.getCreateTime());
            gifts.add(m);
        }
        r.put("gifts", gifts);
        // 本店订单（最近10笔）
        java.util.List<java.util.Map<String, Object>> orders = new java.util.ArrayList<>();
        for (com.ibigou.blindbox.entity.IbigouOrder o : ibigouOrderRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo)) {
            if (orders.size() >= 10) break;
            if (!phone.equals(o.getUserPhone())) continue;
            var m = new java.util.HashMap<String, Object>();
            m.put("orderNo", o.getIbigouOrderNo());
            m.put("amount", o.getPayAmount());
            m.put("ts", o.getCreateTime());
            orders.add(m);
        }
        for (com.ibigou.blindbox.entity.OfflineOrder o : offlineOrderRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo)) {
            if (orders.size() >= 10) break;
            if (!phone.equals(o.getUserPhone())) continue;
            var m = new java.util.HashMap<String, Object>();
            m.put("orderNo", o.getOfflineOrderNo());
            m.put("amount", o.getPayAmount());
            m.put("ts", o.getCreateTime());
            orders.add(m);
        }
        r.put("orders", orders);
        return Result.ok(r);
    }

    /** 商家端：保存会员资料（喜好/家人喜好/备注） */
    @PostMapping("/members/{phone}/profile")
    public Result<Void> saveMemberProfile(@RequestHeader("X-Merchant-Token") String token,
                                          @PathVariable String phone,
                                          @RequestBody java.util.Map<String, String> body) {
        String merchantNo = authService.merchantNoByToken(token);
        var prof = memberProfileRepository.findByMerchantNoAndUserPhone(merchantNo, phone)
                .orElseGet(() -> {
                    var p = new com.ibigou.blindbox.entity.MemberProfile();
                    p.setMerchantNo(merchantNo);
                    p.setUserPhone(phone);
                    return p;
                });
        prof.setName(body.get("name"));
        prof.setGender(body.get("gender"));
        prof.setCustomerPref(body.get("customerPref"));
        prof.setFamilyPref(body.get("familyPref"));
        prof.setBirthday(body.get("birthday"));
        prof.setRemark(body.get("remark"));
        var mEnt = merchantRepository.findByMerchantNo(merchantNo).orElse(null);
        if (mEnt != null) prof.setEditor(mEnt.getLoginAccount());
        prof.setUpdateTime(java.time.LocalDateTime.now());
        memberProfileRepository.save(prof);
        return Result.ok(null);
    }

    /** 商家端：新增回访记录 */
    @PostMapping("/members/{phone}/visit")
    public Result<Void> addMemberVisit(@RequestHeader("X-Merchant-Token") String token,
                                       @PathVariable String phone,
                                       @RequestBody java.util.Map<String, String> body) {
        String merchantNo = authService.merchantNoByToken(token);
        var v = new com.ibigou.blindbox.entity.MemberVisit();
        v.setMerchantNo(merchantNo);
        v.setUserPhone(phone);
        v.setContent(body.getOrDefault("content", ""));
        v.setResult(body.get("result"));
        String vt = body.get("visitTime");
        v.setVisitTime(vt == null || vt.isEmpty() ? java.time.LocalDateTime.now() : java.time.LocalDateTime.parse(vt));
        v.setCreateTime(java.time.LocalDateTime.now());
        memberVisitRepository.save(v);
        // 回访附带赠送（可选）
        String gId = body.get("giftGoodsId");
        String gName = body.get("giftGoodsName");
        if (gName != null && !gName.isEmpty()) {
            var g = new com.ibigou.blindbox.entity.MemberGift();
            g.setMerchantNo(merchantNo);
            g.setUserPhone(phone);
            g.setGoodsId(gId == null || gId.isEmpty() ? null : Long.valueOf(gId));
            g.setGoodsName(gName);
            try { g.setQuantity(Integer.valueOf(body.getOrDefault("giftQuantity", "1"))); }
            catch (Exception ex) { g.setQuantity(1); }
            g.setRemark(body.get("giftRemark"));
            g.setSource("visit");
            g.setCreateTime(java.time.LocalDateTime.now());
            memberGiftRepository.save(g);
        }
        return Result.ok(null);
    }

    /** 商家端：新增预约 */
    @PostMapping("/members/{phone}/appointment")
    public Result<Void> addMemberAppointment(@RequestHeader("X-Merchant-Token") String token,
                                             @PathVariable String phone,
                                             @RequestBody java.util.Map<String, Object> body) {
        String merchantNo = authService.merchantNoByToken(token);
        var a = new com.ibigou.blindbox.entity.MemberAppointment();
        a.setMerchantNo(merchantNo);
        a.setUserPhone(phone);
        a.setApptTime(java.time.LocalDateTime.parse((String) body.get("apptTime")));
        a.setServiceItem((String) body.get("serviceItem"));
        a.setRemark((String) body.get("remark"));
        a.setStatus(0);
        a.setRemindMinutes(body.get("remindMinutes") == null ? 30 : ((Number) body.get("remindMinutes")).intValue());
        a.setRemindSent(0);
        a.setCreateTime(java.time.LocalDateTime.now());
        memberAppointmentRepository.save(a);
        return Result.ok(null);
    }

    /** 商家端：独立赠送礼品（生日或其他时间） */
    @PostMapping("/members/{phone}/gift")
    public Result<Void> addMemberGift(@RequestHeader("X-Merchant-Token") String token,
                                      @PathVariable String phone,
                                      @RequestBody java.util.Map<String, Object> body) {
        String merchantNo = authService.merchantNoByToken(token);
        var g = new com.ibigou.blindbox.entity.MemberGift();
        g.setMerchantNo(merchantNo);
        g.setUserPhone(phone);
        Object gid = body.get("goodsId");
        g.setGoodsId(gid == null ? null : Long.valueOf(String.valueOf(gid)));
        g.setGoodsName(String.valueOf(body.get("goodsName")));
        Object qty = body.get("quantity");
        try { g.setQuantity(qty == null ? 1 : Integer.valueOf(String.valueOf(qty))); }
        catch (Exception ex) { g.setQuantity(1); }
        g.setRemark((String) body.get("remark"));
        g.setSource("gift");
        g.setCreateTime(java.time.LocalDateTime.now());
        memberGiftRepository.save(g);
        return Result.ok(null);
    }

    /** 商家端：预约状态变更（1完成 2取消） */
    @PostMapping("/appointments/{id}/status")
    public Result<Void> updateAppointmentStatus(@RequestHeader("X-Merchant-Token") String token,
                                                @PathVariable Long id,
                                                @RequestBody java.util.Map<String, Object> body) {
        String merchantNo = authService.merchantNoByToken(token);
        var a = memberAppointmentRepository.findById(id).orElseThrow(() -> new RuntimeException("预约不存在"));
        if (!merchantNo.equals(a.getMerchantNo())) throw new RuntimeException("无权操作该预约");
        a.setStatus(((Number) body.get("status")).intValue());
        memberAppointmentRepository.save(a);
        return Result.ok(null);
    }

    /** 商家端：近期预约（待服务，近7天） */
    @GetMapping("/appointments/upcoming")
    public Result<java.util.List<java.util.Map<String, Object>>> upcomingAppointments(@RequestHeader("X-Merchant-Token") String token) {
        String merchantNo = authService.merchantNoByToken(token);
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (var a : memberAppointmentRepository.findByMerchantNoAndStatusOrderByApptTimeAsc(merchantNo, 0)) {
            if (a.getApptTime().isAfter(java.time.LocalDateTime.now().plusDays(7))) continue;
            var m = new java.util.HashMap<String, Object>();
            m.put("id", a.getId());
            m.put("userPhone", a.getUserPhone());
            m.put("apptTime", a.getApptTime());
            m.put("serviceItem", a.getServiceItem());
            m.put("remark", a.getRemark());
            m.put("remindMinutes", a.getRemindMinutes());
            m.put("status", a.getStatus());
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 商家端：订单列表（宜必购商城订单 + 线下订单 合并，新→旧，含真实奖品名） */
    @GetMapping("/ibigou/orders")
    public Result<java.util.List<java.util.Map<String, Object>>> ibigouOrders(@RequestHeader("X-Merchant-Token") String token) {
        String merchantNo = authService.merchantNoByToken(token);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        // 宜必购商城订单
        for (com.ibigou.blindbox.entity.IbigouOrder o : ibigouOrderRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo)) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("orderNo", o.getIbigouOrderNo());
            m.put("userPhone", o.getUserPhone());
            m.put("amount", o.getPayAmount());
            m.put("save", o.getOrderAmount().subtract(o.getPayAmount()).max(BigDecimal.ZERO));
            m.put("ts", o.getCreateTime());
            m.put("status", o.getRefundStatus());
            m.put("couponId", o.getCouponId());
            m.put("source", "ibigou");
            resolvePrize(merchantNo, m, o.getCouponId());
            result.add(m);
        }
        // 线下订单（核销券场景也有奖品）
        for (com.ibigou.blindbox.entity.OfflineOrder o : offlineOrderRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo)) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("orderNo", o.getOfflineOrderNo());
            m.put("userPhone", o.getUserPhone());
            m.put("amount", o.getPayAmount());
            m.put("save", o.getOrderAmount().subtract(o.getPayAmount()).max(BigDecimal.ZERO));
            m.put("ts", o.getCreateTime());
            m.put("status", o.getOrderStatus());
            m.put("couponId", o.getCouponId());
            m.put("source", "offline");
            resolvePrize(merchantNo, m, o.getCouponId());
            result.add(m);
        }
        // 按时间倒序
        result.sort((a, b) -> {
            Object ta = a.get("ts"); Object tb = b.get("ts");
            if (ta == null) return 1;
            if (tb == null) return -1;
            return ((java.time.LocalDateTime) tb).compareTo((java.time.LocalDateTime) ta);
        });
        return Result.ok(result);
    }

    /** 奖品名/emoji：couponId -> UserCoupon -> 本店/公共奖品池 remark */
    private void resolvePrize(String merchantNo, java.util.Map<String, Object> m, Long couponId) {
        String prizeName = "";
        String prizeEmoji = "🎁";
        if (couponId != null) {
            var uc = userCouponRepository.findById(couponId);
            if (uc.isPresent()) {
                var coupon = uc.get();
                String srcMno = coupon.getSourceMerchantNo() == null ? merchantNo : coupon.getSourceMerchantNo();
                var pools = boxPrizePoolRepository.findByMerchantNoAndPrizeTypeAndPrizeValue(
                        srcMno, coupon.getPrizeType(), coupon.getPrizeValue());
                for (var p : pools) {
                    if (p.getRemark() != null && !p.getRemark().isEmpty()) { prizeName = p.getRemark(); break; }
                }
                int pt = coupon.getPrizeType() == null ? 1 : coupon.getPrizeType();
                String[] emojis = {"", "🎟️", "💵", "💰", "👑"};
                String[] typeNames = {"", "折扣券", "立减券", "余额券", "免单券"};
                if (pt >= 1 && pt <= 4) {
                    prizeEmoji = emojis[pt];
                    if (prizeName.isEmpty()) prizeName = typeNames[pt];
                }
            }
        }
        m.put("prizeName", prizeName);
        m.put("prizeEmoji", prizeEmoji);
    }

    /** 本店订单导出 Excel（P1：线下结算） */
    @GetMapping("/orders/export")
    public org.springframework.http.ResponseEntity<byte[]> exportOrders(
            @RequestHeader("X-Merchant-Token") String token,
            @RequestParam(required = false) String userPhone,
            @RequestParam(required = false) String date) {
        String merchantNo = authService.merchantNoByToken(token);
        java.util.List<OfflineOrder> list = offlineOrderService.listOrders(merchantNo, userPhone, date);
        return reportService.exportOfflineOrders(list, merchantNo);
    }

    /** 录入本单微信/支付宝交易流水号（退款时去商户后台办理） */
    @PostMapping("/order/{orderNo}/trade-no")
    public Result<OfflineOrder> saveTradeNo(@PathVariable String orderNo,
                                            @RequestParam String tradeNo,
                                            @RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(offlineOrderService.saveTradeNo(orderNo, authService.merchantNoByToken(token), tradeNo));
    }

    // ---------------- 模式B：订单凭证核验与确认完成（V1.5） ----------------

    /** 凭证核验（商家扫码/输入 token；仅核验订单明细，不能收款） */
    @GetMapping("/order/voucher")
    public Result<OfflineOrder> voucherInfo(@RequestHeader("X-Merchant-Token") String token,
                                            @RequestParam String voucherToken) {
        authService.merchantNoByToken(token);
        return Result.ok(offlineOrderService.voucherInfo(voucherToken));
    }

    /** 商家确认完成（模式B闭环）：扣余额/核销券/发放返还，凭证作废 */
    @PostMapping("/order/{orderNo}/confirm")
    public Result<OfflineOrder> confirmOrder(@PathVariable String orderNo,
                                             @RequestHeader("X-Merchant-Token") String token) {
        String operator = authService.merchantNoByToken(token);
        return Result.ok(offlineOrderService.confirmOrderB(orderNo, operator));
    }

    // ---------------- 会员续费（V1.5） ----------------

    /** 会员状态（免费试用中/已付费-有效期至/已过期 + 剩余天数） */
    @GetMapping("/member/status")
    public Result<MemberService.MemberStatus> memberStatus(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(memberService.status(authService.merchantNoByToken(token)));
    }

    /** 套餐信息（月单价/年费/赠送活动文案动态输出，前端不写死） */
    @GetMapping("/member/plan")
    public Result<PlanInfo> memberPlan(@RequestHeader("X-Merchant-Token") String token) {
        authService.merchantNoByToken(token);
        return Result.ok(new PlanInfo(memberService.freeTrialDays(), memberService.monthlyPrice(),
                memberService.annualPrice(), memberService.giftSwitchOn(), memberService.giftMonths()));
    }

    /** 生成续费订单（年费 = 月单价×12，含活动赠送月数） */
    @PostMapping("/member/renew-order")
    public Result<MemberOrder> createRenewOrder(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(memberService.createRenewOrder(authService.merchantNoByToken(token)));
    }

    /** 确认支付成功（占位回调；真实微信支付 P1 对接） */
    @PostMapping("/member/renew-order/{orderNo}/confirm")
    public Result<MemberOrder> confirmRenew(@PathVariable String orderNo,
                                            @RequestHeader("X-Merchant-Token") String token) {
        // P0：测试确认仅限测试模式开启时可用（生产必须走微信支付回调）
        if (!"1".equals(configService.get("test_pay_confirm_enabled", "0"))) {
            throw new com.ibigou.blindbox.common.BizException("生产环境禁止测试确认支付，请走微信支付");
        }
        String operator = authService.merchantNoByToken(token);
        return Result.ok(memberService.confirmRenewPaid(orderNo, operator));
    }

    /** 微信支付下单（Native）：返回 code_url；测试模式返回 mock 提示走测试确认 */
    @PostMapping("/member/renew-order/{orderNo}/pay")
    public Result<PayResult> payRenew(@PathVariable String orderNo,
                                      @RequestHeader("X-Merchant-Token") String token) {
        String merchantNo = authService.merchantNoByToken(token);
        MemberOrder order = memberService.getOrder(orderNo, merchantNo);
        if (order.getStatus() != 0) {
            throw new com.ibigou.blindbox.common.BizException("订单状态不可支付");
        }
        String notifyUrl = configService.get("IBIGOU_DOMAIN", "https://ybgtc.com");
        String codeUrl = wxPayService.nativePay(orderNo, order.getAmount(), "宜必购商家年费续费", notifyUrl + "/api/pay/wx/notify");
        if (codeUrl == null) {
            return Result.ok(new PayResult(false, null, "测试模式：微信支付未开启，请用【确认支付(测试)】完成"));
        }
        return Result.ok(new PayResult(true, codeUrl, "ok"));
    }

    /** 续费记录 */
    @GetMapping("/member/orders")
    public Result<java.util.List<MemberOrder>> memberOrders(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(memberService.orders(authService.merchantNoByToken(token)));
    }

    public record PlanInfo(int freeTrialDays, java.math.BigDecimal monthlyPrice, java.math.BigDecimal annualPrice,
                           boolean giftSwitchOn, int giftMonths) {
    }

    /** 支付结果 */
    public record PayResult(boolean realPay, String codeUrl, String msg) {
    }

    public record UserAssets(List<UserCoupon> offlineCoupons, List<UserCoupon> ibigouCoupons, BigDecimal balance) {
    }

    // ---------------- ???????P1? ----------------

    /** ????????????? */
    @GetMapping("/chat/messages")
    public Result<java.util.List<com.ibigou.blindbox.entity.ChatMessage>> chatMessages(
            @RequestHeader("X-Merchant-Token") String token,
            @RequestParam String userPhone) {
        String mno = authService.merchantNoByToken(token);
        return Result.ok(chatService.listForMerchant(userPhone, mno));
    }

    /** ?????? */
    @PostMapping("/chat/send")
    public Result<com.ibigou.blindbox.entity.ChatMessage> chatSend(
            @RequestHeader("X-Merchant-Token") String token,
            @RequestParam String userPhone,
            @RequestParam String content) {
        String mno = authService.merchantNoByToken(token);
        return Result.ok(chatService.sendFromMerchant(userPhone, mno, content));
    }

    /** ???????????? */
    @GetMapping("/chat/sessions")
    public Result<java.util.List<com.ibigou.blindbox.entity.ChatMessage>> chatSessions(
            @RequestHeader("X-Merchant-Token") String token) {
        String mno = authService.merchantNoByToken(token);
        return Result.ok(chatService.listSessions(mno));
    }

    @GetMapping("/chat/unread-count")
    public Result<Long> chatUnread(@RequestHeader("X-Merchant-Token") String token) {
        String mno = authService.merchantNoByToken(token);
        return Result.ok(chatService.unreadForMerchant(mno));
    }
}
