package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import com.ibigou.blindbox.service.PropertyDashboardService;
import com.ibigou.blindbox.service.PropertyDeductEngineService;
import com.ibigou.blindbox.service.PropertyDeviceAlertService;
import com.ibigou.blindbox.service.PropertyReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 物业管理后台 API。
 * 鉴权：X-Property-Token（本控制器内部校验）。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/admin")
@RequiredArgsConstructor
public class PropertyAdminController {

    private final PropertyAdminRepository adminRepository;
    private final PropertyAdminSessionRepository sessionRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyFamilyMemberRepository familyMemberRepository;
    private final PropertyBindRelationRepository bindRelationRepository;
    private final PropertyBillRepository billRepository;
    private final PropertyParkingFeeRepository parkingFeeRepository;
    private final PropertySplitRecordRepository splitRecordRepository;
    private final PropertyDeductLogRepository deductLogRepository;
    private final PropertyActivityAuditRepository activityAuditRepository;
    private final PropertyAccessLogRepository accessLogRepository;
    private final PropertyVehicleRepository vehicleRepository;
    private final PropertyVehicleLogRepository vehicleLogRepository;
    private final PropertyCommunityRepository communityRepository;
    private final PropertyCompanyRepository companyRepository;
    private final PropertyDeductEngineService deductEngineService;
    private final PropertyDashboardService dashboardService;
    private final PropertyReservationService reservationService;
    private final PropertyFacilityRepository facilityRepository;
    private final PropertyReservationRepository reservationRepository;
    private final PropertyDeviceAlertService deviceAlertService;
    private final PropertyDeviceRepository deviceRepository;
    private final PropertyAlertRuleRepository alertRuleRepository;
    private final PropertyDeviceAlertRepository deviceAlertRepository;
    private final PropertyBuildingRepository buildingRepository;
    private final PropertyRoomRepository roomRepository;
    private final PropertyPaymentRepository paymentRepository;
    private final PropertyPrizePoolRepository prizePoolRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ---------------- Token 校验 ----------------

    private PropertyAdmin validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException("未登录");
        }
        PropertyAdminSession session = sessionRepository.findByTokenAndExpireTimeAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BizException("登录已过期，请重新登录"));
        return adminRepository.findById(session.getAdminId())
                .orElseThrow(() -> new BizException("管理员不存在"));
    }

    // ---------------- 1. 登录 ----------------

    @PostMapping("/auth/login")
    public Result<String> login(@RequestParam String account, @RequestParam String password) {
        PropertyAdmin admin = adminRepository.findByAccount(account)
                .orElseThrow(() -> new BizException("账号或密码错误"));
        if (!passwordEncoder.matches(password, admin.getLoginPwd())) {
            throw new BizException("账号或密码错误");
        }
        if (admin.getStatus() != null && admin.getStatus() != 1) {
            throw new BizException("账号已被禁用");
        }

        // 创建会话
        String token = UUID.randomUUID().toString().replace("-", "");
        PropertyAdminSession session = new PropertyAdminSession();
        session.setToken(token);
        session.setAdminId(admin.getAdminId());
        session.setExpireTime(LocalDateTime.now().plusHours(24));
        sessionRepository.save(session);

        // 更新最后登录时间
        admin.setLastLoginTime(LocalDateTime.now());
        adminRepository.save(admin);

        log.info("Property admin login: account={}, adminId={}", account, admin.getAdminId());
        return Result.ok(token);
    }

    // ---------------- 2. Dashboard ----------------

    // ---------------- 物业公司列表（下拉） ----------------

    @GetMapping("/companies")
    public Result<java.util.List<java.util.Map<String, Object>>> companies(@RequestHeader("X-Property-Token") String token) {
        validateToken(token);
        return Result.ok(companyRepository.findAll().stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .map(c -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("companyId", c.getCompanyId());
                    m.put("companyName", c.getCompanyName());
                    m.put("reservationFeeEnabled", c.getReservationFeeEnabled() != null ? c.getReservationFeeEnabled() : 0);
                    return m;
                }).collect(java.util.stream.Collectors.toList()));
    }

    /** 物业公司「场地预约收费开关」：0=免费 1=收费（物业后台配置） */
    @PutMapping("/companies/{id}/reservation-fee")
    public Result<Void> setReservationFee(@RequestHeader("X-Property-Token") String token,
                                          @PathVariable Long id,
                                          @RequestParam Integer enabled) {
        validateToken(token);
        com.ibigou.blindbox.entity.PropertyCompany c = companyRepository.findById(id)
                .orElseThrow(() -> new BizException("物业公司不存在"));
        c.setReservationFeeEnabled(enabled != null && enabled == 1 ? 1 : 0);
        companyRepository.save(c);
        return Result.ok();
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@RequestHeader("X-Property-Token") String token) {
        PropertyAdmin admin = validateToken(token);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        // 今日分账金额
        List<PropertySplitRecord> todaySplits = splitRecordRepository.findAll().stream()
                .filter(s -> s.getCreateTime() != null
                        && !s.getCreateTime().isBefore(todayStart)
                        && !s.getCreateTime().isAfter(todayEnd))
                .collect(Collectors.toList());
        BigDecimal todaySplitAmount = todaySplits.stream()
                .map(PropertySplitRecord::getSplitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 今日扣款金额
        List<PropertyDeductLog> todayDeducts = deductLogRepository.findAll().stream()
                .filter(d -> d.getCreateTime() != null
                        && !d.getCreateTime().isBefore(todayStart)
                        && !d.getCreateTime().isAfter(todayEnd))
                .collect(Collectors.toList());
        BigDecimal todayDeductAmount = todayDeducts.stream()
                .map(PropertyDeductLog::getDeductAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 待审核数
        long pendingAudits = activityAuditRepository.findByAuditStatus(0).size();

        // 业主总数
        long totalOwners = ownerRepository.count();

        Map<String, Object> data = new HashMap<>();
        data.put("todaySplitAmount", todaySplitAmount);
        data.put("todayDeductAmount", todayDeductAmount);
        data.put("pendingAudits", pendingAudits);
        data.put("totalOwners", totalOwners);
        return Result.ok(data);
    }

    // ---------------- 3. 业主列表 ----------------

    @GetMapping("/owners")
    public Result<List<PropertyOwner>> owners(@RequestHeader("X-Property-Token") String token,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String keyword) {
        validateToken(token);
        List<PropertyOwner> list = ownerRepository.findAll();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            list = list.stream()
                    .filter(o -> (o.getOwnerName() != null && o.getOwnerName().toLowerCase().contains(kw))
                            || (o.getOwnerPhone() != null && o.getOwnerPhone().contains(kw)))
                    .collect(Collectors.toList());
        }
        // P0: simple in-memory pagination
        int from = Math.min(page * size, list.size());
        int to = Math.min(from + size, list.size());
        return Result.ok(list.subList(from, to));
    }

    // ---------------- 4. 家庭成员 ----------------

    @GetMapping("/owners/{id}/family")
    public Result<List<PropertyFamilyMember>> ownerFamily(@RequestHeader("X-Property-Token") String token,
                                                          @PathVariable Long id) {
        validateToken(token);
        return Result.ok(familyMemberRepository.findByOwnerIdAndStatus(id, 1));
    }

    // ---------------- 5. 绑定请求列表 ----------------

    @GetMapping("/bind-requests")
    public Result<List<PropertyBindRelation>> bindRequests(@RequestHeader("X-Property-Token") String token) {
        validateToken(token);
        // status=0 表示待审核/待确认的绑定请求
        List<PropertyBindRelation> all = bindRelationRepository.findAll();
        List<PropertyBindRelation> pending = all.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 0)
                .collect(Collectors.toList());
        return Result.ok(pending);
    }

    // ---------------- 6. 审批绑定请求 ----------------

    @PutMapping("/bind-requests/{id}/approve")
    public Result<Void> approveBindRequest(@RequestHeader("X-Property-Token") String token,
                                           @PathVariable Long id) {
        validateToken(token);
        PropertyBindRelation relation = bindRelationRepository.findById(id)
                .orElseThrow(() -> new BizException("绑定关系不存在"));
        relation.setStatus(1);
        bindRelationRepository.save(relation);
        log.info("Bind request approved: bindId={}", id);
        return Result.ok();
    }

    // ---------------- 7. 拒绝绑定请求 ----------------

    @PutMapping("/bind-requests/{id}/reject")
    public Result<Void> rejectBindRequest(@RequestHeader("X-Property-Token") String token,
                                          @PathVariable Long id) {
        validateToken(token);
        PropertyBindRelation relation = bindRelationRepository.findById(id)
                .orElseThrow(() -> new BizException("绑定关系不存在"));
        relation.setStatus(2); // 2= rejected
        bindRelationRepository.save(relation);
        log.info("Bind request rejected: bindId={}", id);
        return Result.ok();
    }

    // ---------------- 8. 生成账单 ----------------

    @PostMapping("/bills/generate")
    public Result<Map<String, Object>> generateBills(@RequestHeader("X-Property-Token") String token,
                                                     @RequestParam Long companyId,
                                                     @RequestParam String billPeriod,
                                                     @RequestParam Integer billType,
                                                     @RequestParam BigDecimal amount) {
        PropertyAdmin admin = validateToken(token);

        // 查询该公司下所有已绑定（status=1）的业主
        List<PropertyBindRelation> relations = bindRelationRepository.findByCompanyIdAndStatus(companyId, 1);
        if (relations.isEmpty()) {
            throw new BizException("该公司下无已绑定的业主，无法生成账单");
        }

        // 计算账单到期日：账期月份的最后一天
        LocalDate dueDate = java.time.YearMonth.parse(billPeriod, DateTimeFormatter.ofPattern("yyyy-MM")).atEndOfMonth();

        List<PropertyBill> bills = new java.util.ArrayList<>();
        for (PropertyBindRelation rel : relations) {
            PropertyBill bill = new PropertyBill();
            bill.setBillNo(UUID.randomUUID().toString().replace("-", ""));
            bill.setOwnerId(rel.getOwnerId());
            bill.setCompanyId(companyId);
            bill.setRoomId(rel.getRoomId() != null ? rel.getRoomId() : 0L);
            bill.setBillPeriod(billPeriod);
            bill.setBillType(billType);
            bill.setAmount(amount);
            bill.setStatus(0); // pending
            bill.setDueDate(dueDate);
            bills.add(bill);
        }

        billRepository.saveAll(bills);
        log.info("Batch bills generated: companyId={}, period={}, count={}, operator={}",
                companyId, billPeriod, bills.size(), admin.getAdminId());

        Map<String, Object> data = new HashMap<>();
        data.put("count", bills.size());
        data.put("billPeriod", billPeriod);
        data.put("companyId", companyId);
        return Result.ok(data);
    }

    // ---------------- 9. 账单列表 ----------------

    @GetMapping("/bills")
    public Result<?> bills(@RequestHeader("X-Property-Token") String token,
                           @RequestParam(required = false) Long companyId,
                           @RequestParam(required = false) String period,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        validateToken(token);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        if (companyId != null && period != null && !period.isBlank()) {
            return Result.ok(billRepository.findByCompanyIdAndBillPeriod(companyId, period, pageable));
        }
        if (companyId != null) {
            return Result.ok(billRepository.findByCompanyId(companyId, pageable));
        }
        return Result.ok(billRepository.findAll(pageable));
    }

    // ---------------- 物业收款二维码（扫码开盲盒交物业费） ----------------

    /** 生成公司专属收款二维码 SVG：content 指向 H5 扫码页 draw-pay.html?companyId=X */
    @GetMapping(value = "/pay-qr.svg", produces = "image/svg+xml;charset=UTF-8")
    public org.springframework.http.ResponseEntity<String> payQrSvg(@RequestHeader("X-Property-Token") String token,
                                                                   @RequestParam Long companyId) {
        validateToken(token);
        String content = "https://ybgtc.com/h5/property/customer/pay.html?companyId=" + companyId;
        String svg = com.ibigou.blindbox.common.QrSvgUtil.toSvg(content, 260);
        return org.springframework.http.ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(svg);
    }

    // ---------------- 开奖配置（物业专用，复刻商家奖品池） ----------------

    @GetMapping("/prize-pools")
    public Result<java.util.List<PropertyPrizePool>> prizePools(@RequestHeader("X-Property-Token") String token,
                                                                 @RequestParam Long companyId) {
        validateToken(token);
        return Result.ok(prizePoolRepository.findByCompanyIdOrderByChannelAscPoolTypeAscCreateTimeDesc(companyId));
    }

    /** 新增/编辑奖品：prizeType 0普通 1折扣券 2立减券 3普通余额 4团购免单余额；channel 0通用 1美团 2团购 3抖音；poolType 0本店 1公共 */
    @PostMapping("/prize-pools")
    public Result<PropertyPrizePool> savePrizePool(@RequestHeader("X-Property-Token") String token,
                                                    @RequestParam Long companyId,
                                                    @RequestParam(required = false) Long prizeId,
                                                    @RequestParam Integer prizeType,
                                                    @RequestParam(required = false) java.math.BigDecimal prizeValue,
                                                    @RequestParam(required = false) Integer weight,
                                                    @RequestParam String remark,
                                                    @RequestParam(defaultValue = "0") Integer poolType,
                                                    @RequestParam(defaultValue = "0") Integer channel) {
        validateToken(token);
        if (channel == null || channel < 0 || channel > 3) throw new BizException("渠道不合法（0通用 1美团 2团购 3抖音）");
        if (prizeType == null || prizeType < 0 || prizeType > 4) throw new BizException("奖品类型不合法（0普通 1折扣 2立减 3余额 4团购免单）");
        PropertyPrizePool p = prizeId == null ? new PropertyPrizePool()
                : prizePoolRepository.findById(prizeId).orElseThrow(() -> new BizException("奖品不存在"));
        p.setCompanyId(companyId);
        p.setPrizeType(prizeType);
        p.setPrizeValue(prizeValue == null ? java.math.BigDecimal.ZERO : prizeValue);
        p.setWeight(weight == null ? 1 : weight);
        p.setRemark(remark);
        p.setPoolType(poolType);
        p.setChannel(channel);
        if (p.getCreateTime() == null) p.setCreateTime(java.time.LocalDateTime.now());
        p.setUpdateTime(java.time.LocalDateTime.now());
        return Result.ok(prizePoolRepository.save(p));
    }

    @PostMapping("/prize-pools/{id}/enable")
    public Result<Void> enablePrize(@RequestHeader("X-Property-Token") String token, @PathVariable Long id) {
        validateToken(token);
        PropertyPrizePool p = prizePoolRepository.findById(id).orElseThrow(() -> new BizException("奖品不存在"));
        p.setEnabled(1);
        p.setUpdateTime(java.time.LocalDateTime.now());
        prizePoolRepository.save(p);
        return Result.ok();
    }

    @PostMapping("/prize-pools/{id}/disable")
    public Result<Void> disablePrize(@RequestHeader("X-Property-Token") String token, @PathVariable Long id) {
        validateToken(token);
        PropertyPrizePool p = prizePoolRepository.findById(id).orElseThrow(() -> new BizException("奖品不存在"));
        p.setEnabled(0);
        p.setUpdateTime(java.time.LocalDateTime.now());
        prizePoolRepository.save(p);
        return Result.ok();
    }

    @DeleteMapping("/prize-pools/{id}")
    public Result<Void> deletePrize(@RequestHeader("X-Property-Token") String token, @PathVariable Long id) {
        validateToken(token);
        prizePoolRepository.deleteById(id);
        return Result.ok();
    }

    /** 开奖配置汇总（按渠道分组：奖品数/总权重/启用数），作中奖报表基础 */
    @GetMapping("/prize-stats")
    public Result<java.util.List<java.util.Map<String, Object>>> prizeStats(@RequestHeader("X-Property-Token") String token,
                                                                            @RequestParam Long companyId) {
        validateToken(token);
        java.util.List<PropertyPrizePool> all = prizePoolRepository.findByCompanyIdOrderByChannelAscPoolTypeAscCreateTimeDesc(companyId);
        java.util.Map<Integer, java.util.List<PropertyPrizePool>> byChannel = new java.util.LinkedHashMap<>();
        for (PropertyPrizePool p : all) {
            byChannel.computeIfAbsent(p.getChannel() == null ? 0 : p.getChannel(), k -> new java.util.ArrayList<>()).add(p);
        }
        String[] names = {"通用", "美团", "团购", "抖音"};
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        byChannel.forEach((ch, list) -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("channel", ch);
            m.put("channelName", ch >= 0 && ch < names.length ? names[ch] : "未知");
            m.put("count", list.size());
            m.put("enabledCount", list.stream().filter(x -> x.getEnabled() != null && x.getEnabled() == 1).count());
            m.put("totalWeight", list.stream().filter(x -> x.getEnabled() != null && x.getEnabled() == 1)
                    .mapToInt(x -> x.getWeight() == null ? 1 : x.getWeight()).sum());
            out.add(m);
        });
        return Result.ok(out);
    }

    // ---------------- 缴费记录（对账） ----------------

    @GetMapping("/payments")
    public Result<?> payments(@RequestHeader("X-Property-Token") String token,
                              @RequestParam(required = false) Long companyId,
                              @RequestParam(required = false) Integer status,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        validateToken(token);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<PropertyPayment> p;
        if (companyId != null && status != null) {
            p = paymentRepository.findByCompanyIdAndStatus(companyId, status, pageable);
        } else if (companyId != null) {
            p = paymentRepository.findByCompanyId(companyId, pageable);
        } else if (status != null) {
            p = paymentRepository.findByStatus(status, pageable);
        } else {
            p = paymentRepository.findAll(pageable);
        }
        List<Map<String, Object>> items = p.getContent().stream().map(pm -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("paymentNo", pm.getPaymentNo());
            m.put("ownerId", pm.getOwnerId());
            m.put("roomId", pm.getRoomId());
            m.put("billId", pm.getBillId());
            m.put("billNo", pm.getBillNo());
            m.put("amount", pm.getAmount());
            m.put("channel", pm.getChannel());
            m.put("status", pm.getStatus());
            m.put("payTime", pm.getPayTime());
            m.put("createTime", pm.getCreateTime());
            Integer ft = pm.getFeeType() == null ? 0 : pm.getFeeType();
            m.put("feeType", ft);
            m.put("typeLabel", ft == 1 ? "车位费" : "物业费");
            ownerRepository.findById(pm.getOwnerId()).ifPresent(o -> m.put("ownerName", o.getOwnerName()));
            roomRepository.findById(pm.getRoomId()).ifPresent(r -> m.put("roomNo", r.getRoomNo()));
            if (ft == 1) {
                parkingFeeRepository.findById(pm.getBillId()).ifPresent(f -> m.put("billPeriod", f.getPeriod()));
            } else {
                billRepository.findById(pm.getBillId()).ifPresent(b -> m.put("billPeriod", b.getBillPeriod()));
            }
            return m;
        }).collect(Collectors.toList());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            items = items.stream().filter(m -> {
                String ownerName = String.valueOf(m.getOrDefault("ownerName", ""));
                String roomNo = String.valueOf(m.getOrDefault("roomNo", ""));
                String billNo = String.valueOf(m.getOrDefault("billNo", ""));
                String paymentNo = String.valueOf(m.getOrDefault("paymentNo", ""));
                return ownerName.contains(kw) || roomNo.contains(kw) || billNo.contains(kw) || paymentNo.contains(kw);
            }).collect(Collectors.toList());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", items);
        result.put("totalElements", p.getTotalElements());
        result.put("totalPages", p.getTotalPages());
        result.put("number", p.getNumber());
        result.put("size", p.getSize());
        return Result.ok(result);
    }


    // ---------------- 小区管理 ----------------

    @GetMapping("/communities")
    public Result<List<PropertyCommunity>> communities(@RequestHeader("X-Property-Token") String token,
                                                        @RequestParam Long companyId) {
        validateToken(token);
        return Result.ok(communityRepository.findByCompanyId(companyId));
    }

    @PostMapping("/communities")
    public Result<PropertyCommunity> createCommunity(@RequestHeader("X-Property-Token") String token,
                                                      @RequestParam Long companyId,
                                                      @RequestParam String communityName,
                                                      @RequestParam String communityAddr) {
        validateToken(token);
        PropertyCommunity c = new PropertyCommunity();
        c.setCompanyId(companyId);
        c.setCommunityName(communityName);
        c.setCommunityAddr(communityAddr);
        c.setStatus(1);
        return Result.ok(communityRepository.save(c));
    }

    @DeleteMapping("/communities/{id}")
    public Result<Void> deleteCommunity(@RequestHeader("X-Property-Token") String token,
                                         @PathVariable Long id) {
        validateToken(token);
        if (!buildingRepository.findByCommunityId(id).isEmpty()) {
            throw new BizException("该小区下还有楼栋，请先删除楼栋");
        }
        communityRepository.deleteById(id);
        return Result.ok();
    }

    // ---------------- 楼栋管理 ----------------

    @GetMapping("/buildings")
    public Result<List<PropertyBuilding>> buildings(@RequestHeader("X-Property-Token") String token,
                                                     @RequestParam Long communityId) {
        validateToken(token);
        return Result.ok(buildingRepository.findByCommunityId(communityId));
    }

    @PostMapping("/buildings")
    public Result<PropertyBuilding> createBuilding(@RequestHeader("X-Property-Token") String token,
                                                    @RequestParam Long communityId,
                                                    @RequestParam String buildingName,
                                                    @RequestParam(defaultValue = "1") Integer unitCount,
                                                    @RequestParam(defaultValue = "0") Integer floorCount) {
        validateToken(token);
        PropertyBuilding b = new PropertyBuilding();
        b.setCommunityId(communityId);
        b.setBuildingName(buildingName);
        b.setUnitCount(unitCount != null ? unitCount : 1);
        b.setFloorCount(floorCount != null ? floorCount : 0);
        return Result.ok(buildingRepository.save(b));
    }

    @DeleteMapping("/buildings/{id}")
    public Result<Void> deleteBuilding(@RequestHeader("X-Property-Token") String token,
                                        @PathVariable Long id) {
        validateToken(token);
        if (!roomRepository.findByBuildingId(id).isEmpty()) {
            throw new BizException("该楼栋下还有房间，请先删除房间");
        }
        buildingRepository.deleteById(id);
        return Result.ok();
    }

    // ---------------- 房间管理 ----------------

    @GetMapping("/rooms")
    public Result<List<Map<String, Object>>> rooms(@RequestHeader("X-Property-Token") String token,
                                                    @RequestParam Long buildingId) {
        validateToken(token);
        return Result.ok(roomRepository.findByBuildingId(buildingId).stream().map(r -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("roomId", r.getRoomId());
            m.put("buildingId", r.getBuildingId());
            m.put("unitNo", r.getUnitNo());
            m.put("floorNo", r.getFloorNo());
            m.put("roomNo", r.getRoomNo());
            m.put("roomArea", r.getRoomArea());
            m.put("status", r.getStatus());
            // 绑定业主
            bindRelationRepository.findByRoomIdAndStatus(r.getRoomId(), 1).ifPresent(rel -> {
                ownerRepository.findById(rel.getOwnerId()).ifPresent(o -> {
                    m.put("ownerId", o.getOwnerId());
                    m.put("ownerName", o.getOwnerName());
                    m.put("ownerPhone", o.getOwnerPhone());
                });
            });
            return m;
        }).collect(java.util.stream.Collectors.toList()));
    }

    @PostMapping("/rooms")
    public Result<PropertyRoom> createRoom(@RequestHeader("X-Property-Token") String token,
                                            @RequestParam Long buildingId,
                                            @RequestParam(defaultValue = "1") Integer unitNo,
                                            @RequestParam Integer floorNo,
                                            @RequestParam String roomNo,
                                            @RequestParam(required = false) java.math.BigDecimal roomArea) {
        validateToken(token);
        PropertyRoom r = new PropertyRoom();
        r.setBuildingId(buildingId);
        r.setUnitNo(unitNo != null ? unitNo : 1);
        r.setFloorNo(floorNo);
        r.setRoomNo(roomNo);
        r.setRoomArea(roomArea != null ? roomArea : java.math.BigDecimal.ZERO);
        r.setStatus(1);
        return Result.ok(roomRepository.save(r));
    }

    @DeleteMapping("/rooms/{id}")
    public Result<Void> deleteRoom(@RequestHeader("X-Property-Token") String token,
                                    @PathVariable Long id) {
        validateToken(token);
        roomRepository.deleteById(id);
        return Result.ok();
    }

    // ---------------- 房间绑定业主 ----------------

    @PostMapping("/rooms/{id}/bind")
    public Result<Void> bindRoomOwner(@RequestHeader("X-Property-Token") String token,
                                       @PathVariable Long id,
                                       @RequestParam Long ownerId,
                                       @RequestParam Long companyId) {
        validateToken(token);
        PropertyRoom room = roomRepository.findById(id)
                .orElseThrow(() -> new BizException("房间不存在"));
        // 若该房间已有绑定，先解除
        bindRelationRepository.findByRoomIdAndStatus(room.getRoomId(), 1).ifPresent(oldRel -> {
            oldRel.setStatus(0);
            bindRelationRepository.save(oldRel);
        });
        PropertyBindRelation rel = new PropertyBindRelation();
        rel.setOwnerId(ownerId);
        rel.setCompanyId(companyId);
        rel.setRoomId(room.getRoomId());
        rel.setBindTime(java.time.LocalDateTime.now());
        rel.setStatus(1);
        bindRelationRepository.save(rel);
        return Result.ok();
    }

    // ---------------- 年度批量生成物业费账单 ----------------

    @PostMapping("/bills/generate-year")
    public Result<Map<String, Object>> generateYearBills(@RequestHeader("X-Property-Token") String token,
                                                          @RequestParam Long companyId,
                                                          @RequestParam Integer year,
                                                          @RequestParam java.math.BigDecimal amount,
                                                          @RequestParam(defaultValue = "1") Integer billType) {
        PropertyAdmin admin = validateToken(token);
        List<PropertyCommunity> communities = communityRepository.findByCompanyId(companyId);
        if (communities.isEmpty()) {
            throw new BizException("该公司下无小区，请先创建小区");
        }
        int created = 0;
        int skipped = 0;
        for (PropertyCommunity community : communities) {
            for (PropertyBuilding building : buildingRepository.findByCommunityId(community.getCommunityId())) {
                for (PropertyRoom room : roomRepository.findByBuildingId(building.getBuildingId())) {
                    // 找该房间绑定业主
                    Long ownerId = 0L;
                    java.util.Optional<PropertyBindRelation> rel = bindRelationRepository.findByRoomIdAndStatus(room.getRoomId(), 1);
                    if (rel.isPresent()) {
                        ownerId = rel.get().getOwnerId();
                    }
                    for (int m = 1; m <= 12; m++) {
                        String billPeriod = year + "-" + (m < 10 ? "0" + m : String.valueOf(m));
                        java.util.Optional<PropertyBill> exist = billRepository.findByRoomIdAndBillPeriod(room.getRoomId(), billPeriod);
                        if (exist.isPresent()) {
                            skipped++;
                            continue;
                        }
                        PropertyBill bill = new PropertyBill();
                        bill.setBillNo(java.util.UUID.randomUUID().toString().replace("-", ""));
                        bill.setOwnerId(ownerId);
                        bill.setCompanyId(companyId);
                        bill.setRoomId(room.getRoomId());
                        bill.setBillType(billType);
                        bill.setBillPeriod(billPeriod);
                        bill.setAmount(amount);
                        bill.setStatus(0);
                        bill.setDueDate(java.time.YearMonth.of(year, m).atEndOfMonth());
                        billRepository.save(bill);
                        created++;
                    }
                }
            }
        }
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("created", created);
        data.put("skipped", skipped);
        data.put("operator", admin.getAdminId());
        log.info("Year bills generated: companyId={}, year={}, created={}, skipped={}", companyId, year, created, skipped);
        return Result.ok(data);
    }

    // ---------------- 10. 分账记录 ----------------

    @GetMapping("/splits")
    public Result<List<PropertySplitRecord>> splits(@RequestHeader("X-Property-Token") String token,
                                                    @RequestParam(required = false) Long companyId) {
        validateToken(token);
        if (companyId != null) {
            List<PropertySplitRecord> all = splitRecordRepository.findAll();
            List<PropertySplitRecord> filtered = all.stream()
                    .filter(s -> companyId.equals(s.getCompanyId()))
                    .collect(Collectors.toList());
            return Result.ok(filtered);
        }
        return Result.ok(splitRecordRepository.findAll());
    }

    // ---------------- 11. 扣款日志 ----------------

    @GetMapping("/deduct-logs")
    public Result<List<PropertyDeductLog>> deductLogs(@RequestHeader("X-Property-Token") String token,
                                                      @RequestParam(required = false) Long ownerId) {
        validateToken(token);
        if (ownerId != null) {
            return Result.ok(deductLogRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId));
        }
        return Result.ok(deductLogRepository.findAll());
    }

    // ---------------- 12. 活动审核列表 ----------------

    @GetMapping("/activity-audits")
    public Result<List<PropertyActivityAudit>> activityAudits(@RequestHeader("X-Property-Token") String token,
                                                              @RequestParam(required = false) Integer status) {
        validateToken(token);
        if (status != null) {
            return Result.ok(activityAuditRepository.findByAuditStatus(status));
        }
        return Result.ok(activityAuditRepository.findAll());
    }

    // ---------------- 13. 通过活动审核 ----------------

    @PutMapping("/activity-audits/{id}/approve")
    public Result<Void> approveActivityAudit(@RequestHeader("X-Property-Token") String token,
                                             @PathVariable Long id) {
        PropertyAdmin admin = validateToken(token);
        PropertyActivityAudit audit = activityAuditRepository.findById(id)
                .orElseThrow(() -> new BizException("审核记录不存在"));
        audit.setAuditStatus(1);
        audit.setAuditorId(admin.getAdminId());
        audit.setAuditTime(LocalDateTime.now());
        activityAuditRepository.save(audit);
        log.info("Activity audit approved: auditId={}, auditor={}", id, admin.getAdminId());
        return Result.ok();
    }

    // ---------------- 14. 驳回活动审核 ----------------

    @PutMapping("/activity-audits/{id}/reject")
    public Result<Void> rejectActivityAudit(@RequestHeader("X-Property-Token") String token,
                                            @PathVariable Long id,
                                            @RequestParam String remark) {
        PropertyAdmin admin = validateToken(token);
        PropertyActivityAudit audit = activityAuditRepository.findById(id)
                .orElseThrow(() -> new BizException("审核记录不存在"));
        audit.setAuditStatus(2);
        audit.setAuditRemark(remark);
        audit.setAuditorId(admin.getAdminId());
        audit.setAuditTime(LocalDateTime.now());
        activityAuditRepository.save(audit);
        log.info("Activity audit rejected: auditId={}, auditor={}, remark={}", id, admin.getAdminId(), remark);
        return Result.ok();
    }

    // ---------------- 15. 通行记录 ----------------

    @GetMapping("/access/logs")
    public Result<List<PropertyAccessLog>> accessLogs(@RequestHeader("X-Property-Token") String token,
                                                      @RequestParam Long communityId,
                                                      @RequestParam String startDate,
                                                      @RequestParam String endDate) {
        validateToken(token);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime start = LocalDate.parse(startDate, fmt).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate, fmt).atTime(LocalTime.MAX);
        return Result.ok(accessLogRepository.findByCommunityIdAndPassTimeBetweenOrderByPassTimeDesc(
                communityId, start, end));
    }

    // ---------------- 16. 远程开门 ----------------

    @PostMapping("/access/remote-open")
    public Result<Map<String, Object>> remoteOpen(@RequestHeader("X-Property-Token") String token,
                                                  @RequestParam Long communityId,
                                                  @RequestParam String deviceId) {
        PropertyAdmin admin = validateToken(token);
        log.info("Remote open command sent: communityId={}, deviceId={}, operator={}",
                communityId, deviceId, admin.getAdminId());
        // Hardware adapter will be plugged in per deployment (Hikvision/Dahua SDK)
        Map<String, Object> data = new HashMap<>();
        data.put("status", "command_sent");
        data.put("deviceId", deviceId);
        return Result.ok(data);
    }

    // ---------------- 17. 车辆列表 ----------------

    @GetMapping("/vehicles")
    public Result<List<PropertyVehicle>> vehicles(@RequestHeader("X-Property-Token") String token,
                                                  @RequestParam(required = false) Long companyId) {
        validateToken(token);
        if (companyId != null) {
            // Vehicle doesn't have companyId directly; resolve via bind relations
            List<PropertyBindRelation> relations = bindRelationRepository.findByCompanyIdAndStatus(companyId, 1);
            List<Long> ownerIds = relations.stream()
                    .map(PropertyBindRelation::getOwnerId)
                    .distinct()
                    .collect(Collectors.toList());
            if (ownerIds.isEmpty()) {
                return Result.ok(List.of());
            }
            return Result.ok(vehicleRepository.findByOwnerIdIn(ownerIds));
        }
        return Result.ok(vehicleRepository.findAll());
    }

    // ---------------- 18. 更新车辆状态 ----------------

    @PutMapping("/vehicles/{id}/status")
    public Result<Void> updateVehicleStatus(@RequestHeader("X-Property-Token") String token,
                                            @PathVariable Long id,
                                            @RequestParam Integer status) {
        validateToken(token);
        PropertyVehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new BizException("车辆不存在"));
        vehicle.setStatus(status);
        vehicleRepository.save(vehicle);
        log.info("Vehicle status updated: vehicleId={}, status={}", id, status);
        return Result.ok();
    }

    // ---------------- 19. 停车费列表 ----------------

    @GetMapping("/parking-fees")
    public Result<List<PropertyParkingFee>> parkingFees(@RequestHeader("X-Property-Token") String token,
                                                        @RequestParam(required = false) Long companyId) {
        validateToken(token);
        if (companyId != null) {
            List<PropertyParkingFee> all = parkingFeeRepository.findAll();
            List<PropertyParkingFee> filtered = all.stream()
                    .filter(f -> companyId.equals(f.getCompanyId()))
                    .collect(Collectors.toList());
            return Result.ok(filtered);
        }
        return Result.ok(parkingFeeRepository.findAll());
    }

    // ---------------- 20. 车辆通行记录 ----------------

    @GetMapping("/vehicle-logs")
    public Result<List<PropertyVehicleLog>> vehicleLogs(@RequestHeader("X-Property-Token") String token,
                                                        @RequestParam Long communityId,
                                                        @RequestParam String startDate,
                                                        @RequestParam String endDate) {
        validateToken(token);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime start = LocalDate.parse(startDate, fmt).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate, fmt).atTime(LocalTime.MAX);
        return Result.ok(vehicleLogRepository.findByCommunityIdAndCreateTimeBetweenOrderByCreateTimeDesc(
                communityId, start, end));
    }

    // ---------------- 21. 数据看板聚合接口 ----------------

    @GetMapping("/dashboard/stats")
    public Result<Map<String, Object>> dashboardStats(
            @RequestHeader("X-Property-Token") String token,
            @RequestParam Long companyId) {
        validateToken(token);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("overview", dashboardService.getOverview(companyId));
        stats.put("collection", dashboardService.getCollectionStats(companyId));
        stats.put("split", dashboardService.getSplitStats(companyId));
        stats.put("workorder", dashboardService.getWorkorderStats(companyId));
        return Result.ok(stats);
    }

    // ---------------- 22. 场地管理 ----------------

    @GetMapping("/facilities")
    public Result<List<PropertyFacility>> facilities(@RequestHeader("X-Property-Token") String token,
                                                      @RequestParam Long companyId) {
        validateToken(token);
        return Result.ok(facilityRepository.findByCompanyIdOrderByCreateTimeDesc(companyId));
    }

    @PostMapping("/facilities")
    public Result<PropertyFacility> createFacility(@RequestHeader("X-Property-Token") String token,
                                                    @RequestParam Long companyId,
                                                    @RequestParam String name,
                                                    @RequestParam(required = false) String description,
                                                    @RequestParam(required = false) String location,
                                                    @RequestParam(required = false) Integer capacity,
                                                    @RequestParam BigDecimal price,
                                                    @RequestParam String openTime,
                                                    @RequestParam String closeTime) {
        PropertyAdmin admin = validateToken(token);
        PropertyFacility f = new PropertyFacility();
        f.setCompanyId(companyId);
        f.setName(name);
        f.setDescription(description);
        f.setLocation(location);
        f.setCapacity(capacity != null ? capacity : 0);
        f.setPrice(price);
        f.setOpenTime(LocalTime.parse(openTime));
        f.setCloseTime(LocalTime.parse(closeTime));
        f.setStatus(1);
        facilityRepository.save(f);
        log.info("Facility created: id={}, name={}, operator={}", f.getId(), name, admin.getAdminId());
        return Result.ok(f);
    }

    @PutMapping("/facilities/{id}")
    public Result<PropertyFacility> updateFacility(@RequestHeader("X-Property-Token") String token,
                                                    @PathVariable Long id,
                                                    @RequestParam(required = false) String name,
                                                    @RequestParam(required = false) String description,
                                                    @RequestParam(required = false) String location,
                                                    @RequestParam(required = false) Integer capacity,
                                                    @RequestParam(required = false) BigDecimal price,
                                                    @RequestParam(required = false) String openTime,
                                                    @RequestParam(required = false) String closeTime) {
        validateToken(token);
        PropertyFacility f = facilityRepository.findById(id)
                .orElseThrow(() -> new BizException("场地不存在"));
        if (name != null) f.setName(name);
        if (description != null) f.setDescription(description);
        if (location != null) f.setLocation(location);
        if (capacity != null) f.setCapacity(capacity);
        if (price != null) f.setPrice(price);
        if (openTime != null) f.setOpenTime(LocalTime.parse(openTime));
        if (closeTime != null) f.setCloseTime(LocalTime.parse(closeTime));
        facilityRepository.save(f);
        log.info("Facility updated: id={}", id);
        return Result.ok(f);
    }

    @PutMapping("/facilities/{id}/status")
    public Result<Void> toggleFacilityStatus(@RequestHeader("X-Property-Token") String token,
                                              @PathVariable Long id) {
        validateToken(token);
        PropertyFacility f = facilityRepository.findById(id)
                .orElseThrow(() -> new BizException("场地不存在"));
        f.setStatus(f.getStatus() != null && f.getStatus() == 1 ? 0 : 1);
        facilityRepository.save(f);
        log.info("Facility status toggled: id={}, newStatus={}", id, f.getStatus());
        return Result.ok();
    }

    // ---------------- 23. 预约管理 ----------------

    @GetMapping("/reservations")
    public Result<List<PropertyReservation>> reservations(@RequestHeader("X-Property-Token") String token,
                                                           @RequestParam Long companyId,
                                                           @RequestParam(required = false) String statuses) {
        validateToken(token);
        if (statuses != null && !statuses.isBlank()) {
            List<Integer> statusList = java.util.Arrays.stream(statuses.split(","))
                    .map(String::trim).map(Integer::parseInt).collect(Collectors.toList());
            return Result.ok(reservationRepository.findByCompanyIdAndStatusInOrderByCreateTimeDesc(companyId, statusList));
        }
        // 默认返回所有状态
        List<Integer> allStatuses = List.of(0, 1, 2, 3, 4);
        return Result.ok(reservationRepository.findByCompanyIdAndStatusInOrderByCreateTimeDesc(companyId, allStatuses));
    }

    @PostMapping("/reservations/{id}/confirm")
    public Result<Void> confirmReservation(@RequestHeader("X-Property-Token") String token,
                                            @PathVariable Long id) {
        validateToken(token);
        reservationService.confirmReservation(id);
        return Result.ok();
    }

    @PostMapping("/reservations/{id}/check-in")
    public Result<Void> checkInReservation(@RequestHeader("X-Property-Token") String token,
                                            @PathVariable Long id) {
        validateToken(token);
        reservationService.checkIn(id);
        return Result.ok();
    }

    @PostMapping("/reservations/{id}/complete")
    public Result<Void> completeReservation(@RequestHeader("X-Property-Token") String token,
                                             @PathVariable Long id) {
        validateToken(token);
        reservationService.completeReservation(id);
        return Result.ok();
    }

    /** 预约退款（已支付、未签到/未完成）：payStatus 1→2，status→3 */
    @PostMapping("/reservations/{id}/refund")
    public Result<Void> refundReservation(@RequestHeader("X-Property-Token") String token,
                                           @PathVariable Long id) {
        validateToken(token);
        com.ibigou.blindbox.entity.PropertyReservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new BizException("预约记录不存在"));
        if (r.getPayStatus() == null || r.getPayStatus() != 1) {
            throw new BizException("该预约未支付，无需退款");
        }
        if (r.getStatus() == null || r.getStatus() == 3 || r.getStatus() == 4) {
            throw new BizException("已取消或已完成的预约不能退款");
        }
        reservationService.refundReservation(id);
        return Result.ok();
    }

    /** 后台取消预约（已支付的一并标记退款） */
    @PostMapping("/reservations/{id}/cancel")
    public Result<Void> cancelReservation(@RequestHeader("X-Property-Token") String token,
                                           @PathVariable Long id,
                                           @RequestParam(required = false) String reason) {
        validateToken(token);
        com.ibigou.blindbox.entity.PropertyReservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new BizException("预约记录不存在"));
        if (r.getStatus() == 3 || r.getStatus() == 4) {
            throw new BizException("已取消或已完成的预约不能再取消");
        }
        if (r.getPayStatus() != null && r.getPayStatus() == 1) {
            reservationService.refundReservation(id);
        } else {
            reservationService.cancelReservation(id, reason);
        }
        return Result.ok();
    }

    // ==================== IoT 设备与告警管理 ====================

    /**
     * 22. 设备列表
     */
    @GetMapping("/devices")
    public Result<List<PropertyDevice>> devices(@RequestHeader("X-Property-Token") String token,
                                                 @RequestParam Long companyId) {
        validateToken(token);
        return Result.ok(deviceRepository.findByCompanyIdOrderByCreateTimeDesc(companyId));
    }

    /**
     * 23. 添加设备
     */
    @PostMapping("/devices")
    public Result<PropertyDevice> addDevice(@RequestHeader("X-Property-Token") String token,
                                             @RequestParam Long companyId,
                                             @RequestParam Long communityId,
                                             @RequestParam String deviceNo,
                                             @RequestParam String deviceName,
                                             @RequestParam String deviceType,
                                             @RequestParam(required = false) String location,
                                             @RequestParam(required = false) String brand,
                                             @RequestParam(required = false) String ipAddress) {
        validateToken(token);
        if (deviceRepository.findByDeviceNo(deviceNo).isPresent()) {
            throw new BizException("设备编号已存在");
        }
        PropertyDevice device = new PropertyDevice();
        device.setCompanyId(companyId);
        device.setCommunityId(communityId);
        device.setDeviceNo(deviceNo);
        device.setDeviceName(deviceName);
        device.setDeviceType(deviceType);
        device.setLocation(location);
        device.setBrand(brand);
        device.setIpAddress(ipAddress);
        device.setStatus(1);
        PropertyDevice saved = deviceRepository.save(device);
        log.info("Device added: deviceNo={}, type={}", deviceNo, deviceType);
        return Result.ok(saved);
    }

    /**
     * 24. 更新设备
     */
    @PutMapping("/devices/{id}")
    public Result<PropertyDevice> updateDevice(@RequestHeader("X-Property-Token") String token,
                                                @PathVariable Long id,
                                                @RequestParam(required = false) String deviceName,
                                                @RequestParam(required = false) String deviceType,
                                                @RequestParam(required = false) String location,
                                                @RequestParam(required = false) String brand,
                                                @RequestParam(required = false) String ipAddress,
                                                @RequestParam(required = false) Integer status) {
        validateToken(token);
        PropertyDevice device = deviceRepository.findById(id)
                .orElseThrow(() -> new BizException("设备不存在"));
        if (deviceName != null) device.setDeviceName(deviceName);
        if (deviceType != null) device.setDeviceType(deviceType);
        if (location != null) device.setLocation(location);
        if (brand != null) device.setBrand(brand);
        if (ipAddress != null) device.setIpAddress(ipAddress);
        if (status != null) device.setStatus(status);
        PropertyDevice saved = deviceRepository.save(device);
        log.info("Device updated: id={}", id);
        return Result.ok(saved);
    }

    /**
     * 25. 告警列表（支持过滤）
     */
    @GetMapping("/alerts")
    public Result<List<PropertyDeviceAlert>> alerts(@RequestHeader("X-Property-Token") String token,
                                                     @RequestParam Long companyId,
                                                     @RequestParam(required = false) Integer ackStatus,
                                                     @RequestParam(required = false) String startDate,
                                                     @RequestParam(required = false) String endDate) {
        validateToken(token);
        List<PropertyDeviceAlert> list;
        if (ackStatus != null) {
            list = deviceAlertRepository.findByCompanyIdAndAckStatusOrderByCreateTimeDesc(companyId, ackStatus);
        } else {
            list = deviceAlertRepository.findByCompanyIdAndCreateTimeAfter(companyId, LocalDateTime.of(2020, 1, 1, 0, 0));
        }
        // 日期范围过滤
        if (startDate != null && !startDate.isBlank()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDateTime start = LocalDate.parse(startDate, fmt).atStartOfDay();
            list = list.stream().filter(a -> a.getCreateTime() != null && !a.getCreateTime().isBefore(start))
                    .collect(Collectors.toList());
        }
        if (endDate != null && !endDate.isBlank()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDateTime end = LocalDate.parse(endDate, fmt).atTime(LocalTime.MAX);
            list = list.stream().filter(a -> a.getCreateTime() != null && !a.getCreateTime().isAfter(end))
                    .collect(Collectors.toList());
        }
        return Result.ok(list);
    }

    /**
     * 26. 确认告警
     */
    @PostMapping("/alerts/{id}/ack")
    public Result<Void> acknowledgeAlert(@RequestHeader("X-Property-Token") String token,
                                          @PathVariable Long id) {
        PropertyAdmin admin = validateToken(token);
        deviceAlertService.acknowledgeAlert(id, admin.getAdminId());
        return Result.ok();
    }

    /**
     * 27. 处理完成告警
     */
    @PostMapping("/alerts/{id}/resolve")
    public Result<Void> resolveAlert(@RequestHeader("X-Property-Token") String token,
                                      @PathVariable Long id) {
        PropertyAdmin admin = validateToken(token);
        deviceAlertService.resolveAlert(id, admin.getAdminId());
        return Result.ok();
    }

    /**
     * 28. 告警规则列表
     */
    @GetMapping("/alert-rules")
    public Result<List<PropertyAlertRule>> alertRules(@RequestHeader("X-Property-Token") String token,
                                                       @RequestParam Long companyId) {
        validateToken(token);
        return Result.ok(alertRuleRepository.findByCompanyIdAndStatus(companyId, 1));
    }

    /**
     * 29. 创建告警规则
     */
    @PostMapping("/alert-rules")
    public Result<PropertyAlertRule> createAlertRule(@RequestHeader("X-Property-Token") String token,
                                                      @RequestParam Long companyId,
                                                      @RequestParam String name,
                                                      @RequestParam String alertType,
                                                      @RequestParam(required = false) String deviceType,
                                                      @RequestParam(defaultValue = "1") Integer threshold,
                                                      @RequestParam(defaultValue = "5") Integer windowMinutes,
                                                      @RequestParam(defaultValue = "30") Integer silenceMinutes,
                                                      @RequestParam(defaultValue = "1") Integer autoWorkorder,
                                                      @RequestParam(defaultValue = "0") Integer notifyWechat) {
        validateToken(token);
        PropertyAlertRule rule = new PropertyAlertRule();
        rule.setCompanyId(companyId);
        rule.setName(name);
        rule.setAlertType(alertType);
        rule.setDeviceType(deviceType);
        rule.setThreshold(threshold);
        rule.setWindowMinutes(windowMinutes);
        rule.setSilenceMinutes(silenceMinutes);
        rule.setAutoWorkorder(autoWorkorder);
        rule.setNotifyWechat(notifyWechat);
        rule.setStatus(1);
        PropertyAlertRule saved = alertRuleRepository.save(rule);
        log.info("Alert rule created: name={}, alertType={}", name, alertType);
        return Result.ok(saved);
    }

    /**
     * 30. 更新告警规则
     */
    @PutMapping("/alert-rules/{id}")
    public Result<PropertyAlertRule> updateAlertRule(@RequestHeader("X-Property-Token") String token,
                                                      @PathVariable Long id,
                                                      @RequestParam(required = false) String name,
                                                      @RequestParam(required = false) String alertType,
                                                      @RequestParam(required = false) String deviceType,
                                                      @RequestParam(required = false) Integer threshold,
                                                      @RequestParam(required = false) Integer windowMinutes,
                                                      @RequestParam(required = false) Integer silenceMinutes,
                                                      @RequestParam(required = false) Integer autoWorkorder,
                                                      @RequestParam(required = false) Integer notifyWechat,
                                                      @RequestParam(required = false) Integer status) {
        validateToken(token);
        PropertyAlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new BizException("规则不存在"));
        if (name != null) rule.setName(name);
        if (alertType != null) rule.setAlertType(alertType);
        if (deviceType != null) rule.setDeviceType(deviceType);
        if (threshold != null) rule.setThreshold(threshold);
        if (windowMinutes != null) rule.setWindowMinutes(windowMinutes);
        if (silenceMinutes != null) rule.setSilenceMinutes(silenceMinutes);
        if (autoWorkorder != null) rule.setAutoWorkorder(autoWorkorder);
        if (notifyWechat != null) rule.setNotifyWechat(notifyWechat);
        if (status != null) rule.setStatus(status);
        PropertyAlertRule saved = alertRuleRepository.save(rule);
        log.info("Alert rule updated: id={}", id);
        return Result.ok(saved);
    }

    /**
     * 31. 告警统计看板
     */
    @GetMapping("/alerts/stats")
    public Result<Map<String, Object>> alertStats(@RequestHeader("X-Property-Token") String token,
                                                   @RequestParam Long companyId) {
        validateToken(token);
        return Result.ok(deviceAlertService.getAlertStats(companyId));
    }
}
