package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 违停曝光（/api/property/violation）。
 *
 * 链路：业主随手拍 → 识别查登记库 → 登记车自动通知挪走 / 访客通知邀请人 / 无登记转贴单+曝光稿
 * 铁律（写死）：
 *   ① 同车牌 24h 只算 1 条（连续拍同一辆不累积，避免邻居互拍）
 *   ② 不能拍自己的车
 *   ③ 无位置无拍摄时间一律打回
 *   ④ 曝光稿物业审核后署名发布：车牌打星、不出现人像
 *   ⑤ 曝光位每月 ≤3 条（上多了业主反感，威慑力反而下降）
 */
@Slf4j
@RestController
@RequestMapping("/api/property/violation")
@RequiredArgsConstructor
public class PropertyViolationController {

    private final PropertyParkingViolationRepository vioRepository;
    private final PropertyVehicleRepository vehicleRepository;
    private final PropertyVisitorVehicleRepository visitorVehicleRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyRoomRepository roomRepository;
    private final PropertyBindRelationRepository bindRelationRepository;
    private final PropertyAdminRepository adminRepository;
    private final PropertyAdminSessionRepository sessionRepository;

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

    /** 车牌打星：浙A·76F28 → 浙A·76F** */
    static String maskPlate(String plate) {
        if (plate == null || plate.length() <= 2) return "***";
        int keep = Math.max(2, plate.length() - 2);
        StringBuilder sb = new StringBuilder(plate.substring(0, keep));
        for (int i = keep; i < plate.length(); i++) sb.append('*');
        return sb.toString();
    }


    /** 业主侧 companyId 可省略：缺省取该业主第一个有效绑定 */
    private Long ownerCompany(Long ownerId, Long companyId) {
        if (companyId != null) return companyId;
        return bindRelationRepository.findFirstByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1)
                .map(PropertyBindRelation::getCompanyId)
                .orElseThrow(() -> new BizException("请先在「我的」绑定物业公司"));
    }
    // ================== 业主端 ==================

    /**
     * 随手拍提交。识别 + 自动处置一步完成。
     * 返回 route：moved(已通知车主) / visitor(已通知邀请人) / ticket(转贴单待审) / duplicate(24h 重复)
     */
    @PostMapping("/owner/submit")
    public Result<Map<String, Object>> submit(@RequestHeader("X-Owner-Token") String token,
                                              @RequestParam(required = false) Long companyId,
                                              @RequestParam String location,
                                              @RequestParam String carPlate,
                                              @RequestParam(required = false) String media,
                                              @RequestParam(required = false) String shootTime,
                                              @RequestParam(required = false) Long communityId) {
        Long ownerId = validateOwner(token);
        companyId = ownerCompany(ownerId, companyId);
        if (location == null || location.isBlank()) throw new BizException("必须带现场位置");
        String plate = normalizePlate(carPlate);
        if (plate.length() < 5) throw new BizException("车牌识别不出来，请重拍清晰的车牌");

        // ① 同车牌 24h 去重
        List<PropertyParkingViolation> dup = vioRepository
                .findByCarPlateAndCreateTimeAfter(plate, LocalDateTime.now().minusHours(24));
        if (!dup.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("route", "duplicate");
            out.put("message", "这个车牌 24 小时内已经有人提交过，不重复记录（避免同一辆车被反复拍）");
            out.put("vioId", dup.get(0).getVioId());
            return Result.ok(out);
        }

        PropertyParkingViolation v = new PropertyParkingViolation();
        v.setCompanyId(companyId);
        v.setCommunityId(communityId);
        v.setLocation(location);
        v.setCarPlate(plate);
        v.setPlateMasked(maskPlate(plate));
        v.setSubmitOwnerId(ownerId);
        v.setMedia(media);
        v.setShootTime(shootTime != null && !shootTime.isBlank()
                ? parseTime(shootTime) : LocalDateTime.now());
        PropertyOwner submitOwner = ownerRepository.findById(ownerId).orElse(null);

        // ② 查登记库
        List<PropertyVehicle> vs = vehicleRepository.findByPlateNoOrderByStatusDesc(plate);
        PropertyVehicle reg = vs.isEmpty() ? null : vs.get(0);
        Map<String, Object> out = new LinkedHashMap<>();

        if (reg != null) {
            // ③ 不能拍自己的车
            if (ownerId.equals(reg.getOwnerId())) {
                throw new BizException("这是你自己登记的车，不能提交违停");
            }
            v.setRecognizeResult(1);
            v.setVehicleId(reg.getVehicleId());
            v.setOwnerId(reg.getOwnerId());
            v.setAction(1);
            v.setNotifyTime(LocalDateTime.now());
            v.setStatus(0);
            v.setSubmitRoomNo(roomNoOfOwner(ownerId));
            vioRepository.save(v);
            out.put("route", "moved");
            out.put("message", "已自动通知车主挪车，预计 6 分钟内处理");
            out.put("ownerRoomNo", roomNo(reg.getRoomId()));
        } else {
            // ④ 访客？
            Optional<PropertyVisitorVehicle> visitor = visitorVehicleRepository
                    .findByPlateNoAndStatusAndExpireTimeAfter(plate, 1, LocalDateTime.now());
            if (visitor.isPresent()) {
                v.setRecognizeResult(2);
                v.setOwnerId(visitor.get().getOwnerId());
                v.setAction(2);
                v.setNotifyTime(LocalDateTime.now());
                v.setStatus(0);
                v.setSubmitRoomNo(roomNoOfOwner(ownerId));
                vioRepository.save(v);
                out.put("route", "visitor");
                out.put("message", "访客车，已通知邀请人挪车");
                out.put("inviterRoomNo", roomNo(visitor.get().getRoomId()));
            } else {
                // ⑤ 无登记 → 贴单 + 曝光稿起草
                v.setRecognizeResult(3);
                v.setAction(3);
                v.setStatus(2);
                v.setExposureDraft(draftExposure(location, maskPlate(plate), v.getShootTime()));
                v.setSubmitRoomNo(roomNoOfOwner(ownerId));
                vioRepository.save(v);
                out.put("route", "ticket");
                out.put("message", "登记库查无此车：已转贴单，曝光稿已起草待物业审核（车牌打星）");
                out.put("draft", v.getExposureDraft());
            }
        }
        out.put("vioId", v.getVioId());
        out.put("carPlate", v.getCarPlate());
        out.put("plateMasked", v.getPlateMasked());
        out.put("status", v.getStatus());
        out.put("submitterName", submitOwner != null ? submitOwner.getOwnerName() : null);
        return Result.ok(out);
    }

    @GetMapping("/owner/mine")
    public Result<List<Map<String, Object>>> mySubmissions(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwner(token);
        return Result.ok(vioRepository.findBySubmitOwnerIdOrderByCreateTimeDesc(ownerId)
                .stream().map(this::toMap).collect(Collectors.toList()));
    }

    // ================== 后台 ==================

    @GetMapping("/admin/summary")
    public Result<Map<String, Object>> summary(@RequestHeader("X-Property-Token") String token,
                                               @RequestParam(required = false) Long companyId) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        LocalDateTime monthStart = LocalDateTime.now().toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime dayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<PropertyParkingViolation> month = vioRepository.findByCompanyIdOrderByCreateTimeDesc(companyId)
                .stream().filter(v -> v.getCreateTime() != null && v.getCreateTime().isAfter(monthStart))
                .collect(Collectors.toList());

        long moved = month.stream().filter(v -> Integer.valueOf(1).equals(v.getStatus())).count();
        long visitor = month.stream().filter(v -> Integer.valueOf(2).equals(v.getRecognizeResult())).count();
        long noReg = month.stream().filter(v -> Integer.valueOf(3).equals(v.getRecognizeResult())).count();
        long exposed = month.stream().filter(v -> Integer.valueOf(3).equals(v.getStatus())).count();
        long pending = month.stream().filter(v -> Integer.valueOf(2).equals(v.getStatus())).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("monthTotal", month.size());
        m.put("todayTotal", vioRepository.countByCompanyIdAndCreateTimeAfter(companyId, dayStart));
        m.put("moved", moved);
        m.put("visitor", visitor);
        m.put("noRegister", noReg);
        m.put("exposed", exposed);
        m.put("pendingReview", pending);
        m.put("exposeQuotaLeft", Math.max(0, 3 - exposed));
        return Result.ok(m);
    }

    @GetMapping("/admin/list")
    public Result<List<Map<String, Object>>> adminList(@RequestHeader("X-Property-Token") String token,
                                                       @RequestParam(required = false) Long companyId,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(required = false, defaultValue = "100") Integer limit) {
        validateAdmin(token);
        companyId = resolveCompany(token, companyId);
        List<PropertyParkingViolation> list = status != null
                ? vioRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(companyId, status)
                : vioRepository.findByCompanyIdOrderByCreateTimeDesc(companyId);
        return Result.ok(list.stream().limit(limit).map(this::toMap).collect(Collectors.toList()));
    }

    /**
     * 处置：notify(重发通知) / ticket(仅贴单) / expose(上曝光) / dismiss(误报)
     */
    @PostMapping("/admin/{vioId}/handle")
    public Result<Map<String, Object>> handle(@RequestHeader("X-Property-Token") String token,
                                              @PathVariable Long vioId,
                                              @RequestParam String action,
                                              @RequestParam(required = false) String note,
                                              @RequestParam(required = false) String draft) {
        PropertyAdmin admin = validateAdmin(token);
        PropertyParkingViolation v = vioRepository.findById(vioId)
                .orElseThrow(() -> new BizException("记录不存在"));
        v.setHandlerId(admin.getAdminId());
        v.setHandleNote(note);

        switch (action) {
            case "notify":
                v.setNotifyTime(LocalDateTime.now());
                v.setStatus(0);
                break;
            case "ticket":
                v.setAction(3);
                v.setStatus(5);
                break;
            case "expose": {
                LocalDateTime monthStart = LocalDateTime.now().toLocalDate().withDayOfMonth(1).atStartOfDay();
                long exposed = vioRepository.findByCompanyIdOrderByCreateTimeDesc(v.getCompanyId()).stream()
                        .filter(x -> Integer.valueOf(3).equals(x.getStatus())
                                && x.getCreateTime() != null && x.getCreateTime().isAfter(monthStart))
                        .count();
                if (exposed >= 3) throw new BizException("本月曝光位已用满 3 条，下月再排");
                if (draft != null && !draft.isBlank()) v.setExposureDraft(draft);
                v.setAction(4);
                v.setStatus(3);
                v.setExposurePubTime(LocalDateTime.now());
                break;
            }
            case "dismiss":
                v.setStatus(4);
                break;
            case "moved":
                v.setStatus(1);
                v.setMoveAwayTime(LocalDateTime.now());
                if (v.getNotifyTime() != null) {
                    v.setMinutesUsed((int) java.time.Duration.between(v.getNotifyTime(), v.getMoveAwayTime()).toMinutes());
                }
                break;
            default:
                throw new BizException("未知处置：" + action);
        }
        vioRepository.save(v);
        return Result.ok(toMap(v));
    }

    // ================== 工具 ==================

    private static String normalizePlate(String p) {
        if (p == null) return "";
        return p.replaceAll("[\\s\\-]", "").toUpperCase();
    }

    private static LocalDateTime parseTime(String s) {
        try {
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String draftExposure(String location, String maskedPlate, LocalDateTime shootTime) {
        String t = shootTime == null ? "" : String.format("%02d-%02d %02d:%02d",
                shootTime.getMonthValue(), shootTime.getDayOfMonth(),
                shootTime.getHour(), shootTime.getMinute());
        return "【违停曝光】" + t + "，" + location + "，车牌 " + maskedPlate
                + " 车辆占用禁停区域。物业已多次提醒仍未挪走，现予曝光。"
                + "请车主规范停车，共同维护消防通道畅通。";
    }

    private String roomNo(Long roomId) {
        if (roomId == null) return null;
        return roomRepository.findById(roomId).map(PropertyRoom::getRoomNo).orElse(null);
    }

    private String roomNoOfOwner(Long ownerId) {
        if (ownerId == null) return null;
        return bindRelationRepository.findFirstByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1)
                .map(PropertyBindRelation::getRoomId)
                .map(this::roomNo)
                .orElse(null);
    }

    private Map<String, Object> toMap(PropertyParkingViolation v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vioId", v.getVioId());
        m.put("location", v.getLocation());
        m.put("carPlate", v.getCarPlate());
        m.put("plateMasked", v.getPlateMasked());
        m.put("submitOwnerId", v.getSubmitOwnerId());
        m.put("submitRoomNo", v.getSubmitRoomNo());
        m.put("media", v.getMedia());
        m.put("shootTime", v.getShootTime());
        m.put("recognizeResult", v.getRecognizeResult());
        m.put("ownerId", v.getOwnerId());
        m.put("action", v.getAction());
        m.put("notifyTime", v.getNotifyTime());
        m.put("moveAwayTime", v.getMoveAwayTime());
        m.put("minutesUsed", v.getMinutesUsed());
        m.put("status", v.getStatus());
        m.put("exposureDraft", v.getExposureDraft());
        m.put("exposurePubTime", v.getExposurePubTime());
        m.put("handleNote", v.getHandleNote());
        m.put("createTime", v.getCreateTime());
        return m;
    }
}
