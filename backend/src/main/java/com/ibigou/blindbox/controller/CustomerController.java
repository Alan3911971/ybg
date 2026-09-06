package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.OfflineOrder;
import com.ibigou.blindbox.entity.UserMessage;
import com.ibigou.blindbox.entity.ThirdGroupVerifyRecord;
import com.ibigou.blindbox.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 顾客 H5 API（C 端）：盲盒抽奖 / 团购登记 / 线下自营订单 / 钱包 / 宜必购。
 * 用户唯一标识：手机号（请求体传入，V1.4 无登录体系）。
 */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final DrawService drawService;
    private final GroupRecordService groupRecordService;
    private final OfflineOrderService offlineOrderService;
    private final OrderCalcService orderCalcService;
    private final DynamicQrService dynamicQrService;
    private final CustomerAuthService customerAuthService;
    private final IdentityQrService identityQrService;
    private final UserMessageService userMessageService;
    private final com.ibigou.blindbox.repository.UserCouponRepository couponRepository;
    private final WalletService walletService;
    private final IbigouService ibigouService;
    private final com.ibigou.blindbox.repository.MerchantRepository merchantRepository;
    private final ChatService chatService;

    // ---------------- 短信验证码登录（P0） ----------------

    /** 发送验证码（测试模式返回 code 便于联调；生产对接短信服务商） */
    @PostMapping("/auth/send-code")
    public Result<String> sendCode(@RequestParam String userPhone) {
        return Result.ok(customerAuthService.sendCode(userPhone));
    }

    /** 验证码登录：返回 token（敏感操作需携带 X-User-Token） */
    @PostMapping("/auth/login")
    public Result<String> authLogin(@RequestParam String userPhone, @RequestParam String code) {
        return Result.ok(customerAuthService.login(userPhone, code));
    }

    // ---------------- 盲盒抽奖 ----------------

    /** 普通盲盒开奖 */
    @PostMapping("/draw/normal")
    public Result<DrawResult> drawNormal(@RequestParam String merchantNo, @RequestParam String userPhone) {
        return Result.ok(drawService.drawNormal(merchantNo, userPhone));
    }

    /** 美团(1)/饿了么(2) 专属盲盒开奖 */
    @PostMapping("/draw/group")
    public Result<DrawResult> drawGroup(@RequestParam String merchantNo, @RequestParam String userPhone,
                                        @RequestParam int channel, @RequestParam String qrCodeUniqueKey) {
        return Result.ok(drawService.drawGroup(merchantNo, userPhone, channel));
    }

    // ---------------- 团购登记（只登记不核销；接口不接收券/余额参数，后端硬拦截） ----------------

    @PostMapping("/group/register")
    public Result<ThirdGroupVerifyRecord> registerGroup(@RequestParam String merchantNo,
                                                        @RequestParam String userPhone,
                                                        @RequestParam int channel,
                                                        @RequestParam BigDecimal groupAmount,
                                                        @RequestParam(required = false) String drawBatchNo,
                                                        @RequestParam(required = false) String prizeInfo) {
        String qrKey = "QR-" + merchantNo + "-" + channel;
        return Result.ok(groupRecordService.register(qrKey, merchantNo, userPhone, channel,
                groupAmount, drawBatchNo, prizeInfo));
    }

    // ---------------- 线下自营订单（流程 E / C1） ----------------

    /** V1.5：线下订单试算（只读，不落库；余额抵扣自动四重 min） */
    /** V1.5 模式B：下单生成挂起订单 + 一次性凭证（不扣资产，商家确认完成才结算） */
    @PostMapping("/offline/order-b")
    public Result<OfflineOrder> createOfflineOrderB(@RequestParam String userPhone,
                                                    @RequestParam String merchantNo,
                                                    @RequestParam(required = false) Long couponId,
                                                    @RequestParam BigDecimal orderAmount) {
        return Result.ok(offlineOrderService.createOrderB(userPhone, merchantNo, couponId, orderAmount));
    }

    @PostMapping("/offline/calc")
    public Result<OrderCalcService.OrderCalc> calcOffline(@RequestParam String userPhone,
                                                          @RequestParam String merchantNo,
                                                          @RequestParam(required = false) Long couponId,
                                                          @RequestParam BigDecimal orderAmount) {
        return Result.ok(orderCalcService.calc(userPhone, merchantNo, couponId, orderAmount));
    }

    @PostMapping("/offline/order")
    public Result<OfflineOrder> createOfflineOrder(@RequestParam String userPhone,
                                                   @RequestParam String merchantNo,
                                                   @RequestParam(required = false) Long couponId,
                                                   @RequestParam BigDecimal orderAmount,
                                                   @RequestParam(required = false) BigDecimal paidAmount,
                                                   @RequestParam(required = false) String drawBatchNo) {
        return Result.ok(offlineOrderService.createOrder(userPhone, merchantNo, couponId,
                orderAmount, paidAmount, drawBatchNo));
    }

    @PostMapping("/offline/order/{orderNo}/refund")
    public Result<OfflineOrder> refundOffline(@PathVariable String orderNo,
                                              @RequestParam(required = false) BigDecimal refundAmount) {
        return Result.ok(offlineOrderService.refund(orderNo, refundAmount));
    }

    // ---------------- 长期个人身份二维码（P1） ----------------

    /** 生成长期个人身份码（仅识别身份；钱包页可截图保存） */
    /** 二维码 SVG 生成（顾客端动态码/身份码渲染用，内容由前端拼） */
    @PostMapping("/qr-svg")
    public Result<java.util.Map<String, String>> qrSvg(@RequestParam String content) {
        if (content == null || content.isBlank()) {
            throw new com.ibigou.blindbox.common.BizException("二维码内容不能为空");
        }
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("content", content);
        m.put("svg", com.ibigou.blindbox.common.QrSvgUtil.toSvg(content, 260));
        return Result.ok(m);
    }

    @GetMapping("/identity-qr/{userPhone}")
    public Result<String> identityQr(@PathVariable String userPhone) {
        return Result.ok(identityQrService.issue(userPhone));
    }

    // ---------------- 用户站内消息（P1） ----------------

    @GetMapping("/messages/{userPhone}")
    public Result<java.util.List<UserMessage>> userMessages(@PathVariable String userPhone) {
        return Result.ok(userMessageService.list(userPhone));
    }

    @GetMapping("/messages/{userPhone}/unread-count")
    public Result<Long> userUnreadCount(@PathVariable String userPhone) {
        return Result.ok(userMessageService.unreadCount(userPhone));
    }

    @PostMapping("/messages/{id}/read")
    public Result<Void> userMarkRead(@PathVariable Long id, @RequestParam String userPhone) {
        userMessageService.markRead(id, userPhone);
        return Result.ok();
    }

    @PostMapping("/messages/read-all")
    public Result<Void> userMarkAllRead(@RequestParam String userPhone) {
        userMessageService.markAllRead(userPhone);
        return Result.ok();
    }

    // ---------------- 动态核销二维码（V1.5 P2） ----------------

    /** 签发用户动态核销码（短期有效，前端定时刷新；仅核销不能付款） */
    @GetMapping("/qr/dynamic/{userPhone}")
    public Result<DynamicQrService.DynamicCode> dynamicQr(@PathVariable String userPhone) {
        return Result.ok(dynamicQrService.issue(userPhone));
    }

    // ---------------- 钱包 ----------------

    @GetMapping("/wallet/{userPhone}")
    public Result<WalletService.Wallet> wallet(@PathVariable String userPhone) {
        return Result.ok(walletService.wallet(userPhone));
    }

    /** 全店最近中奖记录 */
    @GetMapping("/recent-wins/{merchantNo}")
    public Result<java.util.List<RecentWin>> recentWins(@PathVariable String merchantNo) {
        var coupons = couponRepository.findBySourceMerchantNoOrderByCreateTimeDesc(merchantNo);
        var list = coupons.stream().limit(20).map(c -> {
            String prizeName = switch (c.getPrizeType()) {
                case 1 -> c.getPrizeValue() + "折券";
                case 2 -> "立减" + c.getPrizeValue() + "元";
                case 3 -> "余额" + c.getPrizeValue() + "元";
                case 4 -> "团购免单";
                default -> "奖品";
            };
            String phone = c.getUserPhone();
            if (phone != null && phone.length() >= 11) {
                phone = phone.substring(0, 3) + "****" + phone.substring(7);
            }
            return new RecentWin(phone, c.getPrizeType(), c.getPrizeValue(), prizeName,
                    c.getCreateTime(), c.getStatus());
        }).toList();
        return Result.ok(list);
    }

    public record RecentWin(String phone, Integer prizeType, java.math.BigDecimal prizeValue,
                           String prizeName, LocalDateTime createTime, Integer status) {
    }

    // ---------------- 宜必购渠道（流程 G） ----------------

    @GetMapping("/ibigou/goods")
    public Result<?> ibigouGoods() {
        List<com.ibigou.blindbox.entity.IbigouGoods> goods = ibigouService.listGoods();
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (var g : goods) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("goodsId", g.getGoodsId());
            m.put("merchantNo", g.getMerchantNo());
            try {
                var merchant = merchantRepository.findById(g.getMerchantNo()).orElse(null);
                m.put("merchantName", merchant != null ? merchant.getMerchantName() : g.getMerchantNo());
            } catch (Exception e) {
                m.put("merchantName", g.getMerchantNo());
            }
            m.put("goodsName", g.getGoodsName());
            m.put("price", g.getPrice());
            m.put("description", g.getDescription());
            m.put("images", g.getImages());
            m.put("enabled", g.getEnabled());
            m.put("sortOrder", g.getSortOrder());
            m.put("salesCount", g.getSalesCount());
            result.add(m);
        }
        return Result.ok(result);
    }

    @GetMapping("/ibigou/assets/{userPhone}")
    public Result<IbigouService.IbigouAssets> ibigouAssets(@PathVariable String userPhone) {
        return Result.ok(ibigouService.availableAssets(userPhone));
    }

    @PostMapping("/ibigou/order")
    public Result<?> createIbigouOrder(@RequestParam String userPhone,
                                       @RequestParam(required = false) Long couponId,
                                       @RequestParam(required = false) BigDecimal deductBalance,
                                       @RequestParam BigDecimal orderAmount,
                                       @RequestParam(required = false) String drawBatchNo) {
        return Result.ok(ibigouService.createOrder(userPhone, couponId, deductBalance, orderAmount, drawBatchNo));
    }

    @PostMapping("/ibigou/order/{orderNo}/refund")
    public Result<?> refundIbigou(@PathVariable String orderNo,
                                  @RequestParam(required = false) BigDecimal refundAmount) {
        return Result.ok(ibigouService.refund(orderNo, refundAmount));
    }

    /** 我的宜必购订单列表（详情见订单号关联的券/流水） */
    @GetMapping("/ibigou/orders/{userPhone}")
    public Result<?> myIbigouOrders(@PathVariable String userPhone) {
        return Result.ok(ibigouService.myOrders(userPhone));
    }

    // ---------------- ???????P1? ----------------

    /** ?????????????????afterId ??? */
    @GetMapping("/chat/messages")
    public Result<java.util.List<com.ibigou.blindbox.entity.ChatMessage>> chatMessages(
            @RequestHeader(value = "X-User-Token", required = false) String token,
            @RequestParam String merchantNo,
            @RequestParam(required = false) Long afterId) {
        String userPhone = customerAuthService.requireUser(token);
        return Result.ok(chatService.listForCustomer(userPhone, merchantNo, afterId));
    }

    /** ????????? */
    @PostMapping("/chat/send")
    public Result<com.ibigou.blindbox.entity.ChatMessage> chatSend(
            @RequestHeader(value = "X-User-Token", required = false) String token,
            @RequestParam String merchantNo,
            @RequestParam String content) {
        String userPhone = customerAuthService.requireUser(token);
        return Result.ok(chatService.sendFromCustomer(userPhone, merchantNo, content));
    }
}
