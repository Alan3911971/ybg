package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.MerchantPropertyBinding;
import com.ibigou.blindbox.entity.PropertyActivityAudit;
import com.ibigou.blindbox.entity.PropertyCompany;
import com.ibigou.blindbox.entity.PropertyFacility;
import com.ibigou.blindbox.entity.PropertyReservation;
import com.ibigou.blindbox.entity.PropertySplitRecord;
import com.ibigou.blindbox.repository.MerchantPropertyBindingRepository;
import com.ibigou.blindbox.repository.MerchantRepository;
import com.ibigou.blindbox.repository.PropertyActivityAuditRepository;
import com.ibigou.blindbox.repository.PropertyCompanyRepository;
import com.ibigou.blindbox.repository.PropertyFacilityRepository;
import com.ibigou.blindbox.repository.PropertyReservationRepository;
import com.ibigou.blindbox.repository.PropertySplitRecordRepository;
import com.ibigou.blindbox.service.MerchantAuthService;
import com.ibigou.blindbox.service.PropertyReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 物业分账扩展接口（商家端）：双模式绑定管理、分账配置、活动审核、分账统计。
 */
@RestController
@RequestMapping("/api/merchant/property")
@RequiredArgsConstructor
@Slf4j
public class PropertyMerchantExtController {

    private final MerchantRepository merchantRepository;
    private final MerchantPropertyBindingRepository bindingRepository;
    private final PropertyActivityAuditRepository activityAuditRepository;
    private final PropertySplitRecordRepository splitRecordRepository;
    private final MerchantAuthService authService;
    private final PropertyCompanyRepository companyRepository;
    private final PropertyFacilityRepository facilityRepository;
    private final PropertyReservationRepository reservationRepository;
    private final PropertyReservationService reservationService;

    // ---------------- 1. 查询分账配置 ----------------

    /**
     * 查询商家分账配置信息。
     */
    @GetMapping("/split-config")
    public Result<Map<String, Object>> getSplitConfig(@RequestParam String merchantNo) {
        Merchant merchant = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));

        Map<String, Object> config = new HashMap<>();
        config.put("isSplitEnabled", merchant.getIsSplitEnabled());
        config.put("splitRatio", merchant.getSplitRatio());
        config.put("wxSubMchId", merchant.getWxSubMchId());
        config.put("splitAuditStatus", merchant.getSplitAuditStatus());
        return Result.ok(config);
    }

    // ---------------- 2. 更新分账配置 ----------------

    /**
     * 更新商家分账比例并启用分账。
     */
    @PutMapping("/split-config")
    public Result<Void> updateSplitConfig(@RequestParam String merchantNo,
                                          @RequestParam BigDecimal splitRatio) {
        if (splitRatio == null || splitRatio.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("分账比例不能为负数");
        }
        if (splitRatio.compareTo(new BigDecimal("30")) > 0) {
            throw new BizException("分账比例不能超过30%");
        }

        Merchant merchant = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));

        merchant.setIsSplitEnabled(1);
        merchant.setSplitRatio(splitRatio);
        merchant.setUpdateTime(LocalDateTime.now());
        merchantRepository.save(merchant);

        log.info("Split config updated: merchantNo={}, splitRatio={}", merchantNo, splitRatio);
        return Result.ok();
    }

    // ---------------- 3. 提交活动审核申请 ----------------

    /**
     * 商家提交物业活动审核申请。
     */
    @PostMapping("/activity-audit")
    public Result<Void> submitActivityAudit(@RequestParam String merchantNo,
                                            @RequestParam String activityName,
                                            @RequestParam BigDecimal discountLevel,
                                            @RequestParam String startTime,
                                            @RequestParam String endTime,
                                            @RequestParam(required = false) String applyReason) {
        Merchant merchant = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));

        LocalDateTime start;
        LocalDateTime end;
        try {
            start = LocalDateTime.parse(startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            end = LocalDateTime.parse(endTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            throw new BizException("时间格式错误，请使用ISO格式（如2025-01-01T00:00:00）");
        }

        if (end.isBefore(start)) {
            throw new BizException("结束时间不能早于开始时间");
        }

        Long merchantId;
        try {
            merchantId = Long.parseLong(merchantNo);
        } catch (NumberFormatException e) {
            merchantId = (long) merchantNo.hashCode();
        }

        PropertyActivityAudit audit = new PropertyActivityAudit();
        audit.setMerchantId(merchantId);
        audit.setActivityName(activityName);
        audit.setDiscountLevel(discountLevel);
        audit.setStartTime(start);
        audit.setEndTime(end);
        audit.setApplyReason(applyReason);
        audit.setAuditStatus(0); // 待审核
        activityAuditRepository.save(audit);

        log.info("Activity audit submitted: merchantNo={}, activityName={}, auditId={}",
                merchantNo, activityName, audit.getAuditId());
        return Result.ok();
    }

    // ---------------- 4. 分账统计 ----------------

    /**
     * 查询商家分账统计数据（P0：全量扫描过滤）。
     */
    @GetMapping("/split-stats")
    public Result<Map<String, Object>> getSplitStats(@RequestParam String merchantNo) {
        Long merchantId;
        try {
            merchantId = Long.parseLong(merchantNo);
        } catch (NumberFormatException e) {
            merchantId = (long) merchantNo.hashCode();
        }

        List<PropertySplitRecord> allRecords = splitRecordRepository.findAll();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        BigDecimal totalSplitAmount = BigDecimal.ZERO;
        long totalOrders = 0;
        BigDecimal todaySplitAmount = BigDecimal.ZERO;

        for (PropertySplitRecord record : allRecords) {
            if (merchantId.equals(record.getMerchantId())) {
                totalSplitAmount = totalSplitAmount.add(record.getSplitAmount());
                totalOrders++;
                if (record.getCreateTime() != null && !record.getCreateTime().isBefore(todayStart)) {
                    todaySplitAmount = todaySplitAmount.add(record.getSplitAmount());
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSplitAmount", totalSplitAmount);
        stats.put("totalOrders", totalOrders);
        stats.put("todaySplitAmount", todaySplitAmount);
        return Result.ok(stats);
    }

    // ---------------- 5. 双模式绑定管理 ----------------

    /**
     * 查询商家的所有物业绑定（含双模式）。
     */
    @GetMapping("/bindings")
    public Result<List<Map<String, Object>>> getBindings(@RequestParam String merchantNo) {
        List<MerchantPropertyBinding> bindings = bindingRepository.findByMerchantNo(merchantNo);
        List<Map<String, Object>> result = bindings.stream().map(b -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId());
            m.put("companyId", b.getCompanyId());
            m.put("splitMode", b.getSplitMode());
            m.put("splitModeLabel", b.getSplitMode() != null && b.getSplitMode() == 2 ? "平台→物业" : "商家→物业");
            m.put("customSplitRatio", b.getCustomSplitRatio());
            m.put("status", b.getStatus());
            m.put("remark", b.getRemark());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    /**
     * 创建或更新分账绑定（指定模式）。
     * splitMode: 1=商家→物业, 2=平台→物业
     */
    @PostMapping("/bindings")
    public Result<Void> saveBinding(@RequestParam String merchantNo,
                                    @RequestParam Long companyId,
                                    @RequestParam Integer splitMode,
                                    @RequestParam(required = false) BigDecimal customSplitRatio,
                                    @RequestParam(required = false) String remark) {
        if (splitMode == null || (splitMode != 1 && splitMode != 2)) {
            throw new BizException("splitMode 必须为 1(商家→物业) 或 2(平台→物业)");
        }
        merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));

        MerchantPropertyBinding binding = bindingRepository
                .findByMerchantNoAndCompanyId(merchantNo, companyId)
                .filter(b -> splitMode.equals(b.getSplitMode()))
                .orElse(null);

        if (binding == null) {
            binding = new MerchantPropertyBinding();
            binding.setMerchantNo(merchantNo);
            binding.setCompanyId(companyId);
            binding.setSplitMode(splitMode);
        }
        binding.setCustomSplitRatio(customSplitRatio);
        binding.setRemark(remark);
        binding.setStatus(1);
        bindingRepository.save(binding);

        String modeLabel = splitMode == 2 ? "平台→物业" : "商家→物业";
        log.info("Binding saved: merchant={}, company={}, mode={}", merchantNo, companyId, modeLabel);
        return Result.ok();
    }

    /**
     * 停用绑定。
     */
    @PutMapping("/bindings/{id}/disable")
    public Result<Void> disableBinding(@PathVariable Long id) {
        MerchantPropertyBinding binding = bindingRepository.findById(id)
                .orElseThrow(() -> new BizException("绑定记录不存在"));
        binding.setStatus(0);
        bindingRepository.save(binding);
        log.info("Binding disabled: id={}, merchant={}, mode={}", id, binding.getMerchantNo(), binding.getSplitMode());
        return Result.ok();
    }

    /**
     * 按模式查询分账统计（双模式分别统计）。
     */
    @GetMapping("/split-stats-by-mode")
    public Result<Map<String, Object>> getSplitStatsByMode(@RequestParam String merchantNo,
                                                           @RequestParam(required = false) Integer splitMode) {
        List<PropertySplitRecord> allRecords = splitRecordRepository.findAll();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        BigDecimal merchantTotal = BigDecimal.ZERO, platformTotal = BigDecimal.ZERO;
        long merchantOrders = 0, platformOrders = 0;
        BigDecimal merchantToday = BigDecimal.ZERO, platformToday = BigDecimal.ZERO;

        for (PropertySplitRecord r : allRecords) {
            if (!merchantNo.equals(r.getMerchantNo())) continue;
            int mode = r.getSplitMode() != null ? r.getSplitMode() : 1;
            boolean isToday = r.getCreateTime() != null && !r.getCreateTime().isBefore(todayStart);

            if (mode == 2) {
                platformTotal = platformTotal.add(r.getSplitAmount());
                platformOrders++;
                if (isToday) platformToday = platformToday.add(r.getSplitAmount());
            } else {
                merchantTotal = merchantTotal.add(r.getSplitAmount());
                merchantOrders++;
                if (isToday) merchantToday = merchantToday.add(r.getSplitAmount());
            }
        }

        Map<String, Object> stats = new HashMap<>();
        Map<String, Object> m1 = new HashMap<>();
        m1.put("mode", 1); m1.put("label", "商家→物业");
        m1.put("totalAmount", merchantTotal); m1.put("orders", merchantOrders); m1.put("todayAmount", merchantToday);
        Map<String, Object> m2 = new HashMap<>();
        m2.put("mode", 2); m2.put("label", "平台→物业");
        m2.put("totalAmount", platformTotal); m2.put("orders", platformOrders); m2.put("todayAmount", platformToday);

        stats.put("merchantToProperty", m1);
        stats.put("platformToProperty", m2);
        stats.put("grandTotal", merchantTotal.add(platformTotal));
        return Result.ok(stats);
    }

    // ======================== 场地预约（商家端，自选物业公司，无需绑定） ========================

    /** 物业公司列表（启用）：供商家自选 */
    @GetMapping("/companies")
    public Result<List<Map<String, Object>>> companies() {
        List<PropertyCompany> list = companyRepository.findAllByStatusOrderByCreateTimeDesc(1);
        return Result.ok(list.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("companyId", c.getCompanyId());
            m.put("companyName", c.getCompanyName());
            m.put("reservationFeeEnabled", c.getReservationFeeEnabled() != null ? c.getReservationFeeEnabled() : 0);
            return m;
        }).collect(Collectors.toList()));
    }

    /** 场地列表（该公司启用场地） */
    @GetMapping("/facilities")
    public Result<List<PropertyFacility>> facilities(@RequestParam Long companyId) {
        return Result.ok(facilityRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(companyId, 1));
    }

    /** 场地可用时段 */
    @GetMapping("/facilities/{id}/slots")
    public Result<List<String[]>> facilitySlots(@PathVariable Long id, @RequestParam String date) {
        return Result.ok(reservationService.getAvailableSlots(id, LocalDate.parse(date)));
    }

    /** 商家创建预约（自选物业公司；收费公司自动下单返回二维码，免费公司直接登记） */
    @PostMapping("/reservations")
    public Result<Map<String, Object>> createReservation(@RequestHeader("X-Merchant-Token") String token,
                                                         @RequestParam Long companyId,
                                                         @RequestParam Long facilityId,
                                                         @RequestParam String date,
                                                         @RequestParam String startTime,
                                                         @RequestParam String endTime,
                                                         @RequestParam(required = false) String remark,
                                                         @RequestParam(required = false) String channel) {
        String merchantNo = authService.merchantNoByToken(token);
        PropertyReservation r = reservationService.createMerchantReservation(merchantNo, companyId, facilityId,
                LocalDate.parse(date), LocalTime.parse(startTime), LocalTime.parse(endTime), remark);
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("reservationNo", r.getReservationNo());
        m.put("amount", r.getAmount());
        m.put("payStatus", r.getPayStatus());
        m.put("status", r.getStatus());
        m.put("reservedType", r.getReservedType());
        if (r.getPayStatus() != null && r.getPayStatus() == 0) {
            String ch = (channel == null || channel.isBlank()) ? "wechat" : channel;
            m.put("pay", reservationService.createPayOrder(r.getId(), ch));
        }
        return Result.ok(m);
    }

    /** 我的预约（商家） */
    @GetMapping("/reservations")
    public Result<List<PropertyReservation>> myReservations(@RequestHeader("X-Merchant-Token") String token) {
        String merchantNo = authService.merchantNoByToken(token);
        return Result.ok(reservationRepository.findByMerchantNoOrderByCreateTimeDesc(merchantNo));
    }

    /** 取消预约 */
    @PostMapping("/reservations/{id}/cancel")
    public Result<Void> cancelReservation(@RequestHeader("X-Merchant-Token") String token,
                                          @PathVariable Long id,
                                          @RequestParam(required = false) String reason) {
        authService.merchantNoByToken(token);
        reservationService.cancelReservation(id, reason);
        return Result.ok();
    }

    /** 预约支付状态轮询 */
    @GetMapping("/reservations/{id}/pay-status")
    public Result<Map<String, Object>> payStatus(@PathVariable Long id, @RequestParam String paymentNo) {
        return Result.ok(reservationService.queryStatus(paymentNo));
    }
}
