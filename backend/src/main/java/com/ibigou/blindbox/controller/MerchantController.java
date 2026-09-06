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
