package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import com.ibigou.blindbox.service.MerchantAuthService;
import com.ibigou.blindbox.service.MerchantConfigService;
import com.ibigou.blindbox.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家 H5 后台配置与报表 API（V1.4 5.2）。
 * 鉴权：X-Merchant-Token。
 */
@RestController
@RequestMapping("/api/merchant/config")
@RequiredArgsConstructor
public class MerchantConfigController {

    @org.springframework.beans.factory.annotation.Value("${app.domain}")
    private String appDomain;

    private final MerchantAuthService authService;
    private final MerchantConfigService configService;
    private final ReportService reportService;
    private final MerchantRepository merchantRepository;
    private final BoxPrizePoolRepository prizePoolRepository;
    private final BoxPublicPoolRepository publicPoolRepository;
    private final BoxGroupPrizePoolRepository groupPoolRepository;
    private final BoxGroupPoolConfigRepository groupPoolConfigRepository;

    // ---------------- 5.2.1 盲盒奖品池配置 ----------------

    /** 二维码 SVG 生成：入参 content（链接文本），返回 {content, svg} */
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

    @GetMapping("/weights")
    public Result<Merchant> weights(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(merchantRepository.findById(authService.merchantNoByToken(token)).orElseThrow());
    }

    @PostMapping("/weights")
    public Result<Void> saveWeights(@RequestHeader("X-Merchant-Token") String token,
                                    @RequestParam Integer privatePoolWeight,
                                    @RequestParam Integer publicPoolWeight,
                                    @RequestParam Integer boxDiscountTotalWeight,
                                    @RequestParam Integer boxCouponTotalWeight) {
        configService.saveWeights(authService.merchantNoByToken(token), privatePoolWeight, publicPoolWeight,
                boxDiscountTotalWeight, boxCouponTotalWeight);
        return Result.ok();
    }

    @GetMapping("/prize-pools")
    public Result<List<BoxPrizePool>> prizePools(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(prizePoolRepository.findByMerchantNoOrderByPrizeIdDesc(authService.merchantNoByToken(token)));
    }

    @PostMapping("/prize-pools")
    public Result<BoxPrizePool> savePrizePool(@RequestHeader("X-Merchant-Token") String token,
                                              @RequestParam(required = false) Long prizeId,
                                              @RequestParam Integer prizeType,
                                              @RequestParam BigDecimal prizeValue,
                                              @RequestParam Integer weight,
                                              @RequestParam(required = false) Integer isPutPublic,
                                              @RequestParam(required = false) Integer isSupportIbigou,
                                              @RequestParam(required = false) Integer limitScope,
                                              @RequestParam(required = false) Integer limitCycle,
                                              @RequestParam(required = false) Integer limitMax,
                                              @RequestParam(required = false) String remark) {
        return Result.ok(configService.savePrizePool(authService.merchantNoByToken(token), prizeId, prizeType,
                prizeValue, weight, isPutPublic, isSupportIbigou, limitScope, limitCycle, limitMax, remark));
    }

    @PostMapping("/prize-pools/{id}/enable")
    public Result<Void> enablePrizePool(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        configService.togglePrizePool(authService.merchantNoByToken(token), id, true);
        return Result.ok();
    }

    @PostMapping("/prize-pools/{id}/disable")
    public Result<Void> disablePrizePool(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        configService.togglePrizePool(authService.merchantNoByToken(token), id, false);
        return Result.ok();
    }

    @DeleteMapping("/prize-pools/{id}")
    public Result<Void> deletePrizePool(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        configService.deletePrizePool(authService.merchantNoByToken(token), id);
        return Result.ok();
    }

    // ---------------- 5.2.2 我投放出去的公共券 ----------------

    @PostMapping("/prize-pools/{id}/put-public")
    public Result<BoxPublicPool> putPublic(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        return Result.ok(configService.putPublic(authService.merchantNoByToken(token), id));
    }

    @GetMapping("/public-pools")
    public Result<List<BoxPublicPool>> publicPools(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(publicPoolRepository.findBySourceMerchantNoOrderByPublicIdDesc(
                authService.merchantNoByToken(token)));
    }

    @PostMapping("/public-pools/{id}/up")
    public Result<Void> upPublic(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        configService.togglePublic(authService.merchantNoByToken(token), id, true);
        return Result.ok();
    }

    @PostMapping("/public-pools/{id}/down")
    public Result<Void> downPublic(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        configService.togglePublic(authService.merchantNoByToken(token), id, false);
        return Result.ok();
    }

    // ---------------- 5.2.7 美团/饿了么专属盲盒 ----------------

    @GetMapping("/group-pools")
    public Result<List<BoxGroupPrizePool>> groupPools(@RequestHeader("X-Merchant-Token") String token,
                                                      @RequestParam int channel) {
        return Result.ok(groupPoolRepository.findByMerchantNoAndChannelAndEnabled(
                authService.merchantNoByToken(token), channel, 1));
    }

    @PostMapping("/group-pools")
    public Result<BoxGroupPrizePool> saveGroupPool(@RequestHeader("X-Merchant-Token") String token,
                                                   @RequestParam Integer channel,
                                                   @RequestParam(required = false) Long groupPoolId,
                                                   @RequestParam Integer prizeType,
                                                   @RequestParam BigDecimal prizeValue,
                                                   @RequestParam Integer weight,
                                                   @RequestParam(required = false) Integer isSupportIbigou,
                                                   @RequestParam(required = false) Integer limitScope,
                                                   @RequestParam(required = false) Integer limitCycle,
                                                   @RequestParam(required = false) Integer limitMax) {
        return Result.ok(configService.saveGroupPool(authService.merchantNoByToken(token), channel, groupPoolId,
                prizeType, prizeValue, weight, isSupportIbigou, limitScope, limitCycle, limitMax));
    }

    @PostMapping("/group-pools/{id}/enable")
    public Result<Void> enableGroupPool(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        configService.toggleGroupPool(authService.merchantNoByToken(token), id, true);
        return Result.ok();
    }

    @PostMapping("/group-pools/{id}/disable")
    public Result<Void> disableGroupPool(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        configService.toggleGroupPool(authService.merchantNoByToken(token), id, false);
        return Result.ok();
    }

    @DeleteMapping("/group-pools/{id}")
    public Result<Void> deleteGroupPool(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        configService.deleteGroupPool(authService.merchantNoByToken(token), id);
        return Result.ok();
    }

    /** V1.5：门店抵扣与收款参数（百分比/单日上限/收款模式/收款码图） */
    @GetMapping("/deduct-config")
    public Result<Merchant> deductConfig(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(merchantRepository.findById(authService.merchantNoByToken(token)).orElse(null));
    }

    @PostMapping("/deduct-config")
    public Result<Void> saveDeductConfig(@RequestHeader("X-Merchant-Token") String token,
                                         @RequestParam(required = false) Integer balanceDeductPercent,
                                         @RequestParam(required = false) java.math.BigDecimal dailyDeductLimit,
                                         @RequestParam(required = false) Integer receiveMode,
                                         @RequestParam(required = false) String receiveQrImg) {
        configService.saveDeductConfig(authService.merchantNoByToken(token), balanceDeductPercent,
                dailyDeductLimit, receiveMode, receiveQrImg);
        return Result.ok();
    }

    /** V1.5：专属盲盒大类权重（折扣 vs 立减&余额；0/0 回退按档位权重直抽） */
    @GetMapping("/group-pool-weights")
    public Result<com.ibigou.blindbox.entity.BoxGroupPoolConfig> groupPoolWeights(
            @RequestHeader("X-Merchant-Token") String token, @RequestParam int channel) {
        return Result.ok(groupPoolConfigRepository
                .findByMerchantNoAndChannel(authService.merchantNoByToken(token), channel).orElse(null));
    }

    @PostMapping("/group-pool-weights")
    public Result<Void> saveGroupPoolWeights(@RequestHeader("X-Merchant-Token") String token,
                                             @RequestParam int channel,
                                             @RequestParam Integer discountTotalWeight,
                                             @RequestParam Integer couponBalanceTotalWeight) {
        configService.saveGroupWeights(authService.merchantNoByToken(token), channel,
                discountTotalWeight, couponBalanceTotalWeight);
        return Result.ok();
    }

    /** 专属盲盒二维码地址（ybgtc.com；前端用该 URL 生成二维码图片打印） */
    @GetMapping("/group-qr")
    public Result<String> groupQr(@RequestHeader("X-Merchant-Token") String token,
                                  @RequestParam int channel) {
        String merchantNo = authService.merchantNoByToken(token);
        // 域名走配置（禁硬编码）；页面为真实存在的顾客抽奖页 index.html
        return Result.ok(appDomain + "/h5/customer/index.html?merchantNo=" + merchantNo
                + "&channel=" + channel);
    }

    // ---------------- 5.2.3 外来流入优惠券 ----------------

    @GetMapping("/external-coupons")
    public Result<List<UserCoupon>> externalCoupons(@RequestHeader("X-Merchant-Token") String token) {
        return Result.ok(reportService.externalCoupons(authService.merchantNoByToken(token)));
    }

    @GetMapping("/external-coupons/export")
    public ResponseEntity<byte[]> exportExternalCoupons(@RequestHeader("X-Merchant-Token") String token) {
        return excelResponse("外来流入优惠券.xlsx",
                reportService.exportCoupons(reportService.externalCoupons(authService.merchantNoByToken(token))));
    }

    // ---------------- 5.2.5/5.2.6 余额发放/消费流水 ----------------

    @GetMapping("/balance-flows")
    public Result<List<UserBalanceFlow>> balanceFlows(@RequestHeader("X-Merchant-Token") String token,
                                                      @RequestParam String type,
                                                      @RequestParam(required = false) String from,
                                                      @RequestParam(required = false) String to) {
        String merchantNo = authService.merchantNoByToken(token);
        LocalDateTime f = parse(from, LocalDateTime.now().minusDays(30));
        LocalDateTime t = parse(to, LocalDateTime.now().plusDays(1));
        return Result.ok("grant".equals(type)
                ? reportService.grantFlows(merchantNo, f, t)
                : reportService.consumeFlows(merchantNo, f, t));
    }

    @GetMapping("/balance-flows/export")
    public ResponseEntity<byte[]> exportBalanceFlows(@RequestHeader("X-Merchant-Token") String token,
                                                     @RequestParam String type,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        String merchantNo = authService.merchantNoByToken(token);
        LocalDateTime f = parse(from, LocalDateTime.now().minusDays(30));
        LocalDateTime t = parse(to, LocalDateTime.now().plusDays(1));
        List<UserBalanceFlow> flows = "grant".equals(type)
                ? reportService.grantFlows(merchantNo, f, t)
                : reportService.consumeFlows(merchantNo, f, t);
        return excelResponse("余额流水.xlsx", reportService.exportBalanceFlows(flows));
    }

    // ---------------- 5.2.8 第三方团购登记报表 ----------------

    @GetMapping("/group-records")
    public Result<List<ThirdGroupVerifyRecord>> groupRecords(@RequestHeader("X-Merchant-Token") String token,
                                                             @RequestParam(required = false) Integer channel,
                                                             @RequestParam(required = false) String from,
                                                             @RequestParam(required = false) String to) {
        authService.merchantNoByToken(token);
        return Result.ok(reportService.groupRecords(channel, parse(from, LocalDateTime.now().minusDays(30)),
                parse(to, LocalDateTime.now().plusDays(1))));
    }

    @GetMapping("/group-records/export")
    public ResponseEntity<byte[]> exportGroupRecords(@RequestHeader("X-Merchant-Token") String token,
                                                     @RequestParam(required = false) Integer channel,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        authService.merchantNoByToken(token);
        return excelResponse("团购登记报表.xlsx", reportService.exportGroupRecords(
                reportService.groupRecords(channel, parse(from, LocalDateTime.now().minusDays(30)),
                        parse(to, LocalDateTime.now().plusDays(1)))));
    }

    // ---------------- 5.2.9 宜必购渠道消费对账 ----------------

    @GetMapping("/ibigou-recon")
    public Result<List<IbigouOrder>> ibigouRecon(@RequestHeader("X-Merchant-Token") String token,
                                                 @RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return Result.ok(reportService.ibigouRecon(authService.merchantNoByToken(token),
                parse(from, LocalDateTime.now().minusDays(30)), parse(to, LocalDateTime.now().plusDays(1))));
    }

    @GetMapping("/ibigou-recon/export")
    public ResponseEntity<byte[]> exportIbigouRecon(@RequestHeader("X-Merchant-Token") String token,
                                                    @RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to) {
        return excelResponse("宜必购渠道对账.xlsx", reportService.exportIbigouOrders(
                reportService.ibigouRecon(authService.merchantNoByToken(token),
                        parse(from, LocalDateTime.now().minusDays(30)),
                        parse(to, LocalDateTime.now().plusDays(1)))));
    }

    // ---------------- 工具 ----------------

    private LocalDateTime parse(String s, LocalDateTime def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        return LocalDate.parse(s).atStartOfDay();
    }

    private ResponseEntity<byte[]> excelResponse(String filename, byte[] data) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
