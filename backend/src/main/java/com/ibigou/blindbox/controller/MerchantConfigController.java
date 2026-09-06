package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import com.ibigou.blindbox.entity.BoxPrizeLimitStat;
import com.ibigou.blindbox.repository.BoxPrizeLimitStatRepository;
import com.ibigou.blindbox.service.MerchantAuthService;
import com.ibigou.blindbox.service.MerchantConfigService;
import com.ibigou.blindbox.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
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

    @org.springframework.beans.factory.annotation.Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    private final MerchantAuthService authService;
    private final MerchantConfigService configService;
    private final ReportService reportService;
    private final MerchantRepository merchantRepository;
    private final BoxPrizePoolRepository prizePoolRepository;
    private final BoxPublicPoolRepository publicPoolRepository;
    private final BoxGroupPrizePoolRepository groupPoolRepository;
    private final BoxGroupPoolConfigRepository groupPoolConfigRepository;
    private final BoxPrizeLimitStatRepository statRepository;
    private final IbigouGoodsRepository goodsRepository;

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
                                              @RequestParam(required = false) String remark,
            @RequestParam(required = false) String expireTime) {
        return Result.ok(configService.savePrizePool(authService.merchantNoByToken(token), prizeId, prizeType,
                prizeValue, weight, isPutPublic, isSupportIbigou, limitScope, limitCycle, limitMax, remark,
                expireTime != null && !expireTime.isBlank() ? LocalDateTime.parse(expireTime.replace(' ', 'T')) : null));
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

    // ---------------- 5.3 奖品中奖报表 ----------------

    @GetMapping("/prize-stats")
    public Result<List<Map<String, Object>>> prizeStats(@RequestHeader("X-Merchant-Token") String token) {
        String merchantNo = authService.merchantNoByToken(token);
        List<BoxPrizeLimitStat> stats = statRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo);
        List<BoxPrizePool> pools = prizePoolRepository.findByMerchantNoOrderByPrizeIdDesc(merchantNo);
        Map<Long, BoxPrizePool> poolMap = pools.stream()
                .collect(Collectors.toMap(BoxPrizePool::getPrizeId, p -> p, (a, b) -> a));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (BoxPrizeLimitStat s : stats) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("userPhone", s.getUserPhone());
            row.put("prizeId", s.getPrizeId());
            row.put("poolType", s.getPoolType());
            row.put("prizeType", s.getPrizeType());
            row.put("createTime", s.getCreateTime());
            BoxPrizePool p = poolMap.get(s.getPrizeId());
            if (p != null) {
                row.put("remark", p.getRemark());
                row.put("isPutPublic", p.getIsPutPublic());
            } else {
                row.put("remark", "已删除");
                row.put("isPutPublic", null);
            }
            result.add(row);
        }
        return Result.ok(result);
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

    // ---------------- 商品管理 ----------------

    @GetMapping("/goods")
    public Result<List<IbigouGoods>> listGoods(@RequestHeader("X-Merchant-Token") String token) {
        String mno = authService.merchantNoByToken(token);
        return Result.ok(goodsRepository.findByMerchantNoOrderBySortOrderDesc(mno));
    }

    @PostMapping("/goods")
    public Result<IbigouGoods> createGoods(@RequestHeader("X-Merchant-Token") String token,
                                           @RequestParam String goodsName,
                                           @RequestParam java.math.BigDecimal price,
                                           @RequestParam(required = false) String description,
                                           @RequestParam(required = false) String images) {
        String mno = authService.merchantNoByToken(token);
        IbigouGoods g = new IbigouGoods();
        g.setMerchantNo(mno);
        g.setGoodsName(goodsName);
        g.setPrice(price);
        g.setDescription(description == null ? "" : description);
        g.setImages(images == null ? "" : images);
        g.setEnabled(0);
        g.setSortOrder(0);
        g.setSalesCount(0);
        g.setCreateTime(java.time.LocalDateTime.now());
        g.setUpdateTime(java.time.LocalDateTime.now());
        return Result.ok(goodsRepository.save(g));
    }

    @PostMapping("/goods/{id}")
    public Result<IbigouGoods> updateGoods(@RequestHeader("X-Merchant-Token") String token,
                                           @PathVariable Long id,
                                           @RequestParam(required = false) String goodsName,
                                           @RequestParam(required = false) java.math.BigDecimal price,
                                           @RequestParam(required = false) String description,
                                           @RequestParam(required = false) String images,
                                           @RequestParam(required = false) Integer sortOrder) {
        String mno = authService.merchantNoByToken(token);
        IbigouGoods g = goodsRepository.findById(id).orElseThrow(() -> new com.ibigou.blindbox.common.BizException("商品不存在"));
        if (!mno.equals(g.getMerchantNo())) throw new com.ibigou.blindbox.common.BizException("无权操作");
        if (goodsName != null && !goodsName.isBlank()) g.setGoodsName(goodsName);
        if (price != null) g.setPrice(price);
        if (description != null) g.setDescription(description);
        if (images != null) g.setImages(images);
        if (sortOrder != null) g.setSortOrder(sortOrder);
        g.setUpdateTime(java.time.LocalDateTime.now());
        return Result.ok(goodsRepository.save(g));
    }

    @PostMapping("/goods/{id}/enable")
    public Result<Void> enableGoods(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        String mno = authService.merchantNoByToken(token);
        IbigouGoods g = goodsRepository.findById(id).orElseThrow(() -> new com.ibigou.blindbox.common.BizException("商品不存在"));
        if (!mno.equals(g.getMerchantNo())) throw new com.ibigou.blindbox.common.BizException("无权操作");
        g.setEnabled(1);
        g.setUpdateTime(java.time.LocalDateTime.now());
        goodsRepository.save(g);
        return Result.ok();
    }

    @PostMapping("/goods/{id}/disable")
    public Result<Void> disableGoods(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        String mno = authService.merchantNoByToken(token);
        IbigouGoods g = goodsRepository.findById(id).orElseThrow(() -> new com.ibigou.blindbox.common.BizException("商品不存在"));
        if (!mno.equals(g.getMerchantNo())) throw new com.ibigou.blindbox.common.BizException("无权操作");
        g.setEnabled(0);
        g.setUpdateTime(java.time.LocalDateTime.now());
        goodsRepository.save(g);
        return Result.ok();
    }

    @DeleteMapping("/goods/{id}")
    public Result<Void> deleteGoods(@RequestHeader("X-Merchant-Token") String token, @PathVariable Long id) {
        String mno = authService.merchantNoByToken(token);
        IbigouGoods g = goodsRepository.findById(id).orElseThrow(() -> new com.ibigou.blindbox.common.BizException("商品不存在"));
        if (!mno.equals(g.getMerchantNo())) throw new com.ibigou.blindbox.common.BizException("无权操作");
        goodsRepository.delete(g);
        return Result.ok();
    }

    /** 收款码上传：type=wechat/alipay/unionpay/other */
    @PostMapping("/qr-upload")
    public Result<String> uploadQrCode(@RequestHeader("X-Merchant-Token") String token,
                                        @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                        @RequestParam String type) {
        String mno = authService.merchantNoByToken(token);
        if (!java.util.List.of("wechat", "alipay", "unionpay", "other").contains(type)) {
            throw new com.ibigou.blindbox.common.BizException("收款码类型不合法");
        }
        try {
            String originalName = file.getOriginalFilename();
            String ext = ".png";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String filename = type + ext;
            java.nio.file.Path dir = java.nio.file.Paths.get(uploadDir, "qr", mno).toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path target = dir.resolve(filename);
            file.transferTo(target.toFile());
            String url = "/uploads/qr/" + mno + "/" + filename;
            // Save to merchant entity
            com.ibigou.blindbox.entity.Merchant m = merchantRepository.findById(mno)
                    .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("商家不存在"));
            switch (type) {
                case "wechat" -> m.setReceiveQrImgWechat(url);
                case "alipay" -> m.setReceiveQrImgAlipay(url);
                case "unionpay" -> m.setReceiveQrImgUnionpay(url);
                case "other" -> m.setReceiveQrImgOther(url);
            }
            if (m.getReceiveQrStatus() == null || m.getReceiveQrStatus() == 0) {
                m.setReceiveQrStatus(1);
            }
            m.setReceiveMode(1);
            m.setUpdateTime(java.time.LocalDateTime.now());
            merchantRepository.save(m);
            return Result.ok(url);
        } catch (Exception e) {
            throw new com.ibigou.blindbox.common.BizException("收款码上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/qr-remove")
    public Result<Void> removeQrCode(@RequestHeader("X-Merchant-Token") String token,
                                      @RequestParam String type) {
        String mno = authService.merchantNoByToken(token);
        com.ibigou.blindbox.entity.Merchant m = merchantRepository.findById(mno)
                .orElseThrow(() -> new com.ibigou.blindbox.common.BizException("商家不存在"));
        switch (type) {
            case "wechat" -> m.setReceiveQrImgWechat(null);
            case "alipay" -> m.setReceiveQrImgAlipay(null);
            case "unionpay" -> m.setReceiveQrImgUnionpay(null);
            case "other" -> m.setReceiveQrImgOther(null);
        }
        if (m.getReceiveQrImgWechat() == null && m.getReceiveQrImgAlipay() == null
                && m.getReceiveQrImgUnionpay() == null && m.getReceiveQrImgOther() == null) {
            m.setReceiveQrStatus(0);
        }
        m.setUpdateTime(java.time.LocalDateTime.now());
        merchantRepository.save(m);
        return Result.ok();
    }

    @PostMapping("/goods/upload-image")
    public Result<String> uploadGoodsImage(@RequestHeader("X-Merchant-Token") String token,
                                           @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String mno = authService.merchantNoByToken(token);
        if (file == null || file.isEmpty()) throw new com.ibigou.blindbox.common.BizException("请选择图片");
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf(".")) : ".jpg";
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(uploadDir, "goods", mno).toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(dir);
            String filename = "goods-" + System.currentTimeMillis() + ext;
            file.transferTo(dir.resolve(filename));
            return Result.ok("/uploads/goods/" + mno + "/" + filename);
        } catch (java.io.IOException e) {
            throw new com.ibigou.blindbox.common.BizException("上传失败: " + e.getMessage());
        }
    }


}
