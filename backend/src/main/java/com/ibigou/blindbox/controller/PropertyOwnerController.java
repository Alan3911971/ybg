package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import com.ibigou.blindbox.service.PropertyBillPaymentService;
import com.ibigou.blindbox.service.PropertyCarryOverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 业主 H5 API（物业业主端）：注册 / 登录 / 绑定 / 家庭成员 / 账单 / 通行 / 车辆 / 停车费。
 */
@RestController
@RequestMapping("/api/property/owner")
@RequiredArgsConstructor
@Slf4j
public class PropertyOwnerController {

    private final PropertyOwnerRepository ownerRepository;
    private final PropertyBindRelationRepository bindRelationRepository;
    private final PropertyFamilyMemberRepository familyMemberRepository;
    private final PropertyBillRepository billRepository;
    private final PropertyParkingFeeRepository parkingFeeRepository;
    private final PropertyDeductLogRepository deductLogRepository;
    private final PropertySplitRecordRepository splitRecordRepository;
    private final PropertyAccessCredentialRepository accessCredentialRepository;
    private final PropertyAccessLogRepository accessLogRepository;
    private final PropertyVehicleRepository vehicleRepository;
    private final PropertyCarryOverService carryOverService;
    private final PropertyCompanyRepository companyRepository;
    private final PropertyRoomRepository roomRepository;
    private final PropertyBillPaymentService propertyBillPaymentService;

    static final ConcurrentHashMap<String, Long> TOKEN_STORE = new ConcurrentHashMap<>();
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    // ======================== Token 校验 ========================

    private Long validateOwnerToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException("登录已过期");
        }
        Long ownerId = TOKEN_STORE.get(token);
        if (ownerId == null) {
            throw new BizException("登录已过期");
        }
        return ownerId;
    }

    // ======================== 1. 注册 ========================

    @PostMapping("/register")
    public Result<Void> register(@RequestParam String ownerName,
                                 @RequestParam String ownerPhone,
                                 @RequestParam String password) {
        if (ownerRepository.findByOwnerPhone(ownerPhone).isPresent()) {
            throw new BizException("该手机号已注册");
        }
        PropertyOwner owner = new PropertyOwner();
        owner.setOwnerNo("OW" + System.currentTimeMillis());
        owner.setOwnerName(ownerName);
        owner.setOwnerPhone(ownerPhone);
        owner.setLoginPwd(PASSWORD_ENCODER.encode(password));
        owner.setStatus(1);
        ownerRepository.save(owner);
        log.info("Owner registered: {}", ownerPhone);
        return Result.ok();
    }

    // ======================== 1.5 手机号查询业主状态（免token，供盲盒顾客端合并识别） ========================

    @GetMapping("/lookup")
    public Result<Map<String, Object>> lookup(@RequestParam String phone) {
        Map<String, Object> m = new LinkedHashMap<>();
        PropertyOwner owner = ownerRepository.findByOwnerPhone(phone).orElse(null);
        if (owner == null) {
            // 可能是家庭成员：按手机号在家庭成员表匹配
            Optional<PropertyFamilyMember> fmOpt = familyMemberRepository.findByMemberPhoneAndStatus(phone, 1);
            if (fmOpt.isPresent()) {
                PropertyFamilyMember fm = fmOpt.get();
                m.put("isOwner", false);
                m.put("isFamily", true);
                m.put("familyMemberId", fm.getMemberId());
                m.put("memberName", fm.getMemberName());
                m.put("relation", fm.getRelation());
                m.put("ownerId", fm.getOwnerId());
                ownerRepository.findById(fm.getOwnerId()).ifPresent(o2 -> m.put("ownerName", o2.getOwnerName()));
                fillOwnerBindInfo(m, fm.getOwnerId());
                return Result.ok(m);
            }
            m.put("isOwner", false);
            m.put("isFamily", false);
            m.put("bound", false);
            return Result.ok(m);
        }
        m.put("isOwner", true);
        m.put("isFamily", false);
        m.put("ownerId", owner.getOwnerId());
        m.put("ownerName", owner.getOwnerName());
        fillOwnerBindInfo(m, owner.getOwnerId());
        return Result.ok(m);
    }

    private void fillOwnerBindInfo(Map<String, Object> m, Long ownerId) {
        List<PropertyBindRelation> rels = bindRelationRepository.findByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1);
        boolean bound = !rels.isEmpty();
        m.put("bound", bound);
        if (bound) {
            PropertyBindRelation rel = rels.get(0);
            m.put("companyId", rel.getCompanyId());
            companyRepository.findById(rel.getCompanyId()).ifPresent(c -> m.put("companyName", c.getCompanyName()));
            if (rel.getRoomId() != null) {
                roomRepository.findById(rel.getRoomId()).ifPresent(r -> m.put("roomNo", r.getRoomNo()));
            }
        }
    }

    // ======================== 2. 登录 ========================

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestParam String ownerPhone,
                                             @RequestParam String password) {
        PropertyOwner owner = ownerRepository.findByOwnerPhone(ownerPhone)
                .orElseThrow(() -> new BizException("账号不存在"));
        if (!PASSWORD_ENCODER.matches(password, owner.getLoginPwd())) {
            throw new BizException("密码错误");
        }
        String token = UUID.randomUUID().toString();
        TOKEN_STORE.put(token, owner.getOwnerId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("ownerId", owner.getOwnerId());
        data.put("ownerName", owner.getOwnerName());
        data.put("ownerPhone", owner.getOwnerPhone());
        log.info("Owner logged in: {}", ownerPhone);
        return Result.ok(data);
    }

    // ======================== 3. 个人信息 + 余额摘要 ========================

    @GetMapping("/info")
    public Result<Map<String, Object>> info(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        PropertyOwner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BizException("业主不存在"));
        Map<String, BigDecimal> balanceSummary = carryOverService.getBalanceSummary(ownerId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ownerId", owner.getOwnerId());
        data.put("ownerNo", owner.getOwnerNo());
        data.put("ownerName", owner.getOwnerName());
        data.put("ownerPhone", owner.getOwnerPhone());
        data.put("avatarUrl", owner.getAvatarUrl());
        data.put("status", owner.getStatus());
        data.put("balanceSummary", balanceSummary);
        return Result.ok(data);
    }

    // ======================== 4. 绑定物业公司 ========================

    @PostMapping("/bind")
    public Result<Void> bind(@RequestHeader("X-Owner-Token") String token,
                             @RequestParam Long companyId) {
        Long ownerId = validateOwnerToken(token);
        Optional<PropertyBindRelation> existing = bindRelationRepository.findByOwnerIdAndCompanyId(ownerId, companyId);
        if (existing.isPresent() && existing.get().getStatus() == 1) {
            throw new BizException("已绑定该物业公司");
        }
        PropertyBindRelation relation = new PropertyBindRelation();
        relation.setOwnerId(ownerId);
        relation.setCompanyId(companyId);
        relation.setBindTime(LocalDateTime.now());
        relation.setStatus(1);
        bindRelationRepository.save(relation);
        log.info("Owner {} bound to company {}", ownerId, companyId);
        return Result.ok();
    }

    // ======================== 5. 绑定列表 ========================

    @GetMapping("/bindings")
    public Result<List<PropertyBindRelation>> bindings(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyBindRelation> list = bindRelationRepository.findByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1);
        return Result.ok(list);
    }

    // ======================== 6. 解绑 ========================

    @DeleteMapping("/bindings/{id}")
    public Result<Void> unbind(@RequestHeader("X-Owner-Token") String token,
                               @PathVariable Long id) {
        Long ownerId = validateOwnerToken(token);
        PropertyBindRelation relation = bindRelationRepository.findById(id)
                .orElseThrow(() -> new BizException("绑定关系不存在"));
        if (!relation.getOwnerId().equals(ownerId)) {
            throw new BizException("无权操作");
        }
        relation.setStatus(0);
        bindRelationRepository.save(relation);
        log.info("Owner {} unbound bindId {}", ownerId, id);
        return Result.ok();
    }

    // ======================== 7. 家庭成员列表 ========================

    @GetMapping("/family")
    public Result<List<PropertyFamilyMember>> familyList(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyFamilyMember> members = familyMemberRepository.findByOwnerIdAndStatus(ownerId, 1);
        return Result.ok(members);
    }

    // ======================== 8. 添加家庭成员 ========================

    @PostMapping("/family")
    public Result<PropertyFamilyMember> addFamily(@RequestHeader("X-Owner-Token") String token,
                                                  @RequestParam String memberName,
                                                  @RequestParam(required = false) String memberPhone,
                                                  @RequestParam String relation) {
        Long ownerId = validateOwnerToken(token);
        PropertyFamilyMember member = new PropertyFamilyMember();
        member.setOwnerId(ownerId);
        member.setMemberName(memberName);
        member.setMemberPhone(memberPhone);
        member.setRelation(relation);
        member.setStatus(1);
        familyMemberRepository.save(member);
        log.info("Owner {} added family member: {}", ownerId, memberName);
        return Result.ok(member);
    }

    // ======================== 9. 更新家庭成员 ========================

    @PutMapping("/family/{id}")
    public Result<PropertyFamilyMember> updateFamily(@RequestHeader("X-Owner-Token") String token,
                                                     @PathVariable Long id,
                                                     @RequestParam String memberName,
                                                     @RequestParam(required = false) String memberPhone,
                                                     @RequestParam String relation) {
        Long ownerId = validateOwnerToken(token);
        PropertyFamilyMember member = familyMemberRepository.findById(id)
                .orElseThrow(() -> new BizException("家庭成员不存在"));
        if (!member.getOwnerId().equals(ownerId)) {
            throw new BizException("无权操作");
        }
        member.setMemberName(memberName);
        member.setMemberPhone(memberPhone);
        member.setRelation(relation);
        familyMemberRepository.save(member);
        return Result.ok(member);
    }

    // ======================== 10. 删除家庭成员 ========================

    @DeleteMapping("/family/{id}")
    public Result<Void> deleteFamily(@RequestHeader("X-Owner-Token") String token,
                                     @PathVariable Long id) {
        Long ownerId = validateOwnerToken(token);
        PropertyFamilyMember member = familyMemberRepository.findById(id)
                .orElseThrow(() -> new BizException("家庭成员不存在"));
        if (!member.getOwnerId().equals(ownerId)) {
            throw new BizException("无权操作");
        }
        familyMemberRepository.delete(member);
        return Result.ok();
    }

    // ======================== 10.5 家庭成员扫码绑定（免token） ========================

    /** 扫码绑定页展示信息：业主姓名 + 绑定小区/房号 */
    @GetMapping("/family/qr-info")
    public Result<Map<String, Object>> familyQrInfo(@RequestParam Long ownerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        PropertyOwner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BizException("业主不存在"));
        m.put("ownerId", owner.getOwnerId());
        m.put("ownerName", owner.getOwnerName());
        m.put("ownerPhone", owner.getOwnerPhone());
        List<PropertyBindRelation> rels = bindRelationRepository.findByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1);
        if (!rels.isEmpty()) {
            PropertyBindRelation rel = rels.get(0);
            m.put("companyId", rel.getCompanyId());
            companyRepository.findById(rel.getCompanyId()).ifPresent(c -> m.put("companyName", c.getCompanyName()));
            if (rel.getRoomId() != null) {
                roomRepository.findById(rel.getRoomId()).ifPresent(r -> m.put("roomNo", r.getRoomNo()));
            }
        }
        return Result.ok(m);
    }

    /** 家庭成员扫码后填手机号确认绑定 */
    @PostMapping("/family/bind-by-qr")
    public Result<PropertyFamilyMember> bindFamilyByQr(@RequestParam Long ownerId,
                                                       @RequestParam String memberName,
                                                       @RequestParam String memberPhone,
                                                       @RequestParam String relation) {
        if (memberPhone == null || !memberPhone.matches("1\\d{10}")) {
            throw new BizException("请填写正确的11位手机号");
        }
        if (memberName == null || memberName.isBlank()) {
            throw new BizException("请填写成员姓名");
        }
        if (relation == null || relation.isBlank()) {
            throw new BizException("请选择与业主关系");
        }
        if (!ownerRepository.existsById(ownerId)) {
            throw new BizException("业主不存在");
        }
        Optional<PropertyFamilyMember> dup = familyMemberRepository.findByMemberPhoneAndStatus(memberPhone, 1);
        if (dup.isPresent()) {
            throw new BizException("该手机号已绑定家庭成员，请勿重复绑定");
        }
        PropertyFamilyMember member = new PropertyFamilyMember();
        member.setOwnerId(ownerId);
        member.setMemberName(memberName);
        member.setMemberPhone(memberPhone);
        member.setRelation(relation);
        member.setStatus(1);
        familyMemberRepository.save(member);
        log.info("Family member bound by QR: owner {} phone {}", ownerId, memberPhone);
        return Result.ok(member);
    }

    // ======================== 11. 账单列表 ========================

    @GetMapping("/bills")
    public Result<List<PropertyBill>> bills(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyBill> allBills = billRepository.findAll();
        List<PropertyBill> ownerBills = allBills.stream()
                .filter(b -> b.getOwnerId().equals(ownerId))
                .sorted(Comparator.comparing(PropertyBill::getDueDate))
                .collect(Collectors.toList());
        return Result.ok(ownerBills);
    }

    // ======================== 12. 账单详情 ========================

    @GetMapping("/bills/{id}")
    public Result<PropertyBill> billDetail(@RequestHeader("X-Owner-Token") String token,
                                           @PathVariable Long id) {
        Long ownerId = validateOwnerToken(token);
        PropertyBill bill = billRepository.findById(id)
                .orElseThrow(() -> new BizException("账单不存在"));
        if (!bill.getOwnerId().equals(ownerId)) {
            throw new BizException("无权查看");
        }
        return Result.ok(bill);
    }

    // ======================== 12.5 账单在线缴费 ========================

    /** 创建支付单：channel=wechat|alipay，返回 paymentNo/amount/qrCode */
    @PostMapping("/bills/{id}/pay")
    public Result<Map<String, Object>> payBill(@RequestHeader("X-Owner-Token") String token,
                                               @PathVariable Long id,
                                               @RequestParam String channel) {
        Long ownerId = validateOwnerToken(token);
        return Result.ok(propertyBillPaymentService.createPayment(ownerId, id, channel));
    }

    /** 查询支付单状态（前端轮询） */
    @GetMapping("/bills/{id}/pay-status")
    public Result<Map<String, Object>> payStatus(@RequestHeader("X-Owner-Token") String token,
                                                 @PathVariable Long id,
                                                 @RequestParam String paymentNo) {
        Long ownerId = validateOwnerToken(token);
        return Result.ok(propertyBillPaymentService.queryStatus(ownerId, paymentNo));
    }

    // ======================== 13. 扣款日志 ========================

    @GetMapping("/deduct-logs")
    public Result<List<PropertyDeductLog>> deductLogs(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyDeductLog> logs = deductLogRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId);
        return Result.ok(logs);
    }

    // ======================== 14. 分账汇总 ========================

    @GetMapping("/split-summary")
    public Result<Map<String, Object>> splitSummary(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertySplitRecord> records = splitRecordRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId);
        BigDecimal totalSplitAmount = records.stream()
                .map(PropertySplitRecord::getSplitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSplitAmount", totalSplitAmount);
        summary.put("totalCount", records.size());
        return Result.ok(summary);
    }

    // ======================== 15. 生成通行二维码 ========================

    @PostMapping("/access/qrcode")
    public Result<String> generateQrCode(@RequestHeader("X-Owner-Token") String token,
                                         @RequestParam Long roomId) {
        Long ownerId = validateOwnerToken(token);
        String credValue = UUID.randomUUID().toString();
        PropertyAccessCredential credential = new PropertyAccessCredential();
        credential.setOwnerId(ownerId);
        credential.setRoomId(roomId);
        credential.setCredType(1);
        credential.setCredValue(credValue);
        credential.setExpireTime(LocalDateTime.now().plusSeconds(120));
        credential.setMaxUseCount(1);
        credential.setUsedCount(0);
        credential.setStatus(1);
        accessCredentialRepository.save(credential);
        log.info("Owner {} generated QR credential for room {}", ownerId, roomId);
        return Result.ok(credValue);
    }

    // ======================== 16. 访客邀请凭证 ========================

    @PostMapping("/access/visitor-invite")
    public Result<String> visitorInvite(@RequestHeader("X-Owner-Token") String token,
                                        @RequestParam Long roomId,
                                        @RequestParam(defaultValue = "24") Integer hours) {
        Long ownerId = validateOwnerToken(token);
        String credValue = UUID.randomUUID().toString();
        PropertyAccessCredential credential = new PropertyAccessCredential();
        credential.setOwnerId(ownerId);
        credential.setRoomId(roomId);
        credential.setCredType(2);
        credential.setCredValue(credValue);
        credential.setExpireTime(LocalDateTime.now().plusHours(hours));
        credential.setMaxUseCount(1);
        credential.setUsedCount(0);
        credential.setStatus(1);
        accessCredentialRepository.save(credential);
        log.info("Owner {} generated visitor invite for room {}, valid {}h", ownerId, roomId, hours);
        return Result.ok(credValue);
    }

    // ======================== 17. 我的通行记录 ========================

    @GetMapping("/access/my-logs")
    public Result<List<PropertyAccessLog>> myAccessLogs(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyAccessCredential> credentials = accessCredentialRepository.findByOwnerIdAndStatus(ownerId, 1);
        List<PropertyAccessLog> logs = new ArrayList<>();
        for (PropertyAccessCredential cred : credentials) {
            List<PropertyAccessLog> credLogs = accessLogRepository.findByCredentialId(cred.getCredentialId());
            logs.addAll(credLogs);
        }
        logs.sort(Comparator.comparing(PropertyAccessLog::getPassTime).reversed());
        return Result.ok(logs);
    }

    // ======================== 18. 车辆列表 ========================

    @GetMapping("/vehicles")
    public Result<List<PropertyVehicle>> vehicles(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyVehicle> list = vehicleRepository.findByOwnerIdAndStatus(ownerId, 1);
        return Result.ok(list);
    }

    // ======================== 19. 添加车辆 ========================

    @PostMapping("/vehicles")
    public Result<PropertyVehicle> addVehicle(@RequestHeader("X-Owner-Token") String token,
                                              @RequestParam String plateNo,
                                              @RequestParam(required = false, defaultValue = "1") Integer plateColor,
                                              @RequestParam Long roomId,
                                              @RequestParam Integer parkType,
                                              @RequestParam String validStart,
                                              @RequestParam String validEnd) {
        Long ownerId = validateOwnerToken(token);
        PropertyVehicle vehicle = new PropertyVehicle();
        vehicle.setOwnerId(ownerId);
        vehicle.setPlateNo(plateNo);
        vehicle.setPlateColor(plateColor);
        vehicle.setRoomId(roomId);
        vehicle.setParkType(parkType);
        vehicle.setValidStart(LocalDate.parse(validStart));
        vehicle.setValidEnd(LocalDate.parse(validEnd));
        vehicle.setStatus(1);
        vehicleRepository.save(vehicle);
        log.info("Owner {} added vehicle: {}", ownerId, plateNo);
        return Result.ok(vehicle);
    }

    // ======================== 20. 更新车辆 ========================

    @PutMapping("/vehicles/{id}")
    public Result<PropertyVehicle> updateVehicle(@RequestHeader("X-Owner-Token") String token,
                                                 @PathVariable Long id,
                                                 @RequestParam String plateNo,
                                                 @RequestParam(required = false, defaultValue = "1") Integer plateColor,
                                                 @RequestParam Long roomId,
                                                 @RequestParam Integer parkType,
                                                 @RequestParam String validStart,
                                                 @RequestParam String validEnd) {
        Long ownerId = validateOwnerToken(token);
        PropertyVehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new BizException("车辆不存在"));
        if (!vehicle.getOwnerId().equals(ownerId)) {
            throw new BizException("无权操作");
        }
        vehicle.setPlateNo(plateNo);
        vehicle.setPlateColor(plateColor);
        vehicle.setRoomId(roomId);
        vehicle.setParkType(parkType);
        vehicle.setValidStart(LocalDate.parse(validStart));
        vehicle.setValidEnd(LocalDate.parse(validEnd));
        vehicleRepository.save(vehicle);
        return Result.ok(vehicle);
    }

    // ======================== 21. 删除车辆 ========================

    @DeleteMapping("/vehicles/{id}")
    public Result<Void> deleteVehicle(@RequestHeader("X-Owner-Token") String token,
                                      @PathVariable Long id) {
        Long ownerId = validateOwnerToken(token);
        PropertyVehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new BizException("车辆不存在"));
        if (!vehicle.getOwnerId().equals(ownerId)) {
            throw new BizException("无权操作");
        }
        vehicleRepository.delete(vehicle);
        return Result.ok();
    }

    // ======================== 22. 停车费列表 ========================

    @GetMapping("/parking-fees")
    public Result<List<Map<String, Object>>> parkingFees(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyParkingFee> allFees = parkingFeeRepository.findAll();
        List<PropertyParkingFee> ownerFees = allFees.stream()
                .filter(f -> f.getOwnerId().equals(ownerId))
                .sorted(Comparator.comparing(PropertyParkingFee::getDueDate))
                .collect(Collectors.toList());
        List<Map<String, Object>> list = ownerFees.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("feeId", f.getFeeId());
            m.put("feeNo", f.getFeeNo());
            m.put("ownerId", f.getOwnerId());
            m.put("roomId", f.getRoomId());
            m.put("companyId", f.getCompanyId());
            m.put("feeType", f.getFeeType());
            m.put("period", f.getPeriod());
            m.put("amount", f.getAmount());
            m.put("deducted", f.getDeducted());
            m.put("paid", f.getPaid());
            m.put("dueDate", f.getDueDate());
            m.put("status", f.getStatus());
            m.put("createTime", f.getCreateTime());
            vehicleRepository.findById(f.getVehicleId()).ifPresent(v -> m.put("plateNumber", v.getPlateNo()));
            return m;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    /** 车位费在线缴费：channel=wechat|alipay，返回 paymentNo/amount/qrCode（feeType=1） */
    @PostMapping("/parking-fees/{id}/pay")
    public Result<Map<String, Object>> payParkingFee(@RequestHeader("X-Owner-Token") String token,
                                                      @PathVariable Long id,
                                                      @RequestParam String channel) {
        Long ownerId = validateOwnerToken(token);
        return Result.ok(propertyBillPaymentService.createParkingPayment(ownerId, id, channel));
    }

    /** 车位费支付单状态（前端轮询，与物业费同一 queryStatus） */
    @GetMapping("/parking-fees/{id}/pay-status")
    public Result<Map<String, Object>> parkingPayStatus(@RequestHeader("X-Owner-Token") String token,
                                                         @PathVariable Long id,
                                                         @RequestParam String paymentNo) {
        Long ownerId = validateOwnerToken(token);
        return Result.ok(propertyBillPaymentService.queryStatus(ownerId, paymentNo));
    }
}
