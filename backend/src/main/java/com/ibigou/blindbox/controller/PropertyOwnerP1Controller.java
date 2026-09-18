package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import com.ibigou.blindbox.service.PropertyReservationService;
import com.ibigou.blindbox.service.PropertyTempParkingService;
import com.ibigou.blindbox.service.PropertyValueServiceOrderService;
import com.ibigou.blindbox.service.PropertyWorkorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业主 H5 P1 扩展 API：工单 / 增值服务 / 人脸开门 / 访客车辆 / 临时停车。
 */
@RestController
@RequestMapping("/api/property/owner/p1")
@RequiredArgsConstructor
@Slf4j
public class PropertyOwnerP1Controller {

    private final PropertyWorkorderService workorderService;
    private final PropertyValueServiceOrderService vsOrderService;
    private final PropertyTempParkingService tempParkingService;
    private final PropertyReservationService reservationService;
    private final PropertyFacilityRepository facilityRepository;
    private final PropertyReservationRepository reservationRepository;
    private final PropertyFaceInfoRepository faceInfoRepository;
    private final PropertyVisitorVehicleRepository visitorVehicleRepository;
    private final PropertyWorkorderRepository workorderRepository;
    private final PropertyVsOrderRepository vsOrderRepository;
    private final PropertyValueServiceRepository valueServiceRepository;
    private final PropertyAutoPayBindingRepository autoPayBindingRepository;

    static final ConcurrentHashMap<String, Long> TOKEN_STORE = PropertyOwnerController.TOKEN_STORE;

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

    // ======================== 工单（报修/投诉/建议） ========================

    @PostMapping("/workorders")
    public Result<PropertyWorkorder> createWorkorder(@RequestHeader("X-Owner-Token") String token,
                                                     @RequestParam Long companyId,
                                                     @RequestParam Integer woType,
                                                     @RequestParam String title,
                                                     @RequestParam(required = false) String description,
                                                     @RequestParam(required = false) String images,
                                                     @RequestParam(required = false) Integer urgency,
                                                     @RequestParam(required = false) String category) {
        Long ownerId = validateOwnerToken(token);
        PropertyWorkorder wo = workorderService.createWorkorder(ownerId, companyId, woType,
                title, description, images, urgency != null ? urgency : 1, category);
        return Result.ok(wo);
    }

    @GetMapping("/workorders")
    public Result<List<PropertyWorkorder>> myWorkorders(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyWorkorder> list = workorderRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId);
        return Result.ok(list);
    }

    @GetMapping("/workorders/{id}")
    public Result<PropertyWorkorder> workorderDetail(@RequestHeader("X-Owner-Token") String token,
                                                     @PathVariable Long id) {
        Long ownerId = validateOwnerToken(token);
        PropertyWorkorder wo = workorderRepository.findById(id)
                .orElseThrow(() -> new BizException("工单不存在"));
        if (!wo.getOwnerId().equals(ownerId)) {
            throw new BizException("无权查看");
        }
        return Result.ok(wo);
    }

    @PostMapping("/workorders/{id}/rate")
    public Result<Void> rateWorkorder(@RequestHeader("X-Owner-Token") String token,
                                      @PathVariable Long id,
                                      @RequestParam Integer rating,
                                      @RequestParam(required = false) String comment) {
        Long ownerId = validateOwnerToken(token);
        workorderService.rateWorkorder(id, rating, comment, ownerId);
        return Result.ok();
    }

    // ======================== 增值服务 ========================

    @GetMapping("/value-services")
    public Result<List<PropertyValueService>> availableServices(@RequestParam Long companyId) {
        List<PropertyValueService> services = valueServiceRepository
                .findByCompanyIdAndStatusOrderBySortOrderAsc(companyId, 1);
        return Result.ok(services);
    }

    @PostMapping("/vs-orders")
    public Result<PropertyVsOrder> createVsOrder(@RequestHeader("X-Owner-Token") String token,
                                                  @RequestParam Long companyId,
                                                  @RequestParam Long serviceId,
                                                  @RequestParam Long roomId,
                                                  @RequestParam(required = false) Integer quantity,
                                                  @RequestParam String appointmentTime) {
        Long ownerId = validateOwnerToken(token);
        LocalDateTime appt = LocalDateTime.parse(appointmentTime);
        PropertyVsOrder order = vsOrderService.createOrder(ownerId, companyId, serviceId,
                roomId, quantity != null ? quantity : 1, appt);
        return Result.ok(order);
    }

    @GetMapping("/vs-orders")
    public Result<List<PropertyVsOrder>> myVsOrders(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyVsOrder> list = vsOrderRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId);
        return Result.ok(list);
    }

    @PostMapping("/vs-orders/{orderNo}/pay-balance")
    public Result<Void> payVsOrderWithBalance(@RequestHeader("X-Owner-Token") String token,
                                              @PathVariable String orderNo,
                                              @RequestParam BigDecimal deductAmount) {
        Long ownerId = validateOwnerToken(token);
        vsOrderService.payWithBalance(orderNo, ownerId, deductAmount);
        return Result.ok();
    }

    @PostMapping("/vs-orders/{orderNo}/cancel")
    public Result<Void> cancelVsOrder(@RequestHeader("X-Owner-Token") String token,
                                      @PathVariable String orderNo) {
        Long ownerId = validateOwnerToken(token);
        vsOrderService.cancelOrder(orderNo, ownerId);
        return Result.ok();
    }

    // ======================== 人脸开门 ========================

    @PostMapping("/face/upload")
    public Result<Void> uploadFace(@RequestHeader("X-Owner-Token") String token,
                                   @RequestParam Long roomId,
                                   @RequestParam(required = false) Long memberId) {
        Long ownerId = validateOwnerToken(token);
        PropertyFaceInfo faceInfo = new PropertyFaceInfo();
        faceInfo.setOwnerId(ownerId);
        faceInfo.setRoomId(roomId);
        faceInfo.setMemberId(memberId);
        faceInfo.setAuditStatus(0);
        faceInfo.setStatus(1);
        faceInfoRepository.save(faceInfo);
        log.info("Face info created for owner {}, room {}", ownerId, roomId);
        return Result.ok();
    }

    @GetMapping("/face/status")
    public Result<List<PropertyFaceInfo>> faceStatus(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyFaceInfo> list = faceInfoRepository.findByOwnerIdAndStatus(ownerId, 1);
        return Result.ok(list);
    }

    // ======================== 访客车辆预约 ========================

    @PostMapping("/visitor-vehicles")
    public Result<PropertyVisitorVehicle> createVisitorVehicle(@RequestHeader("X-Owner-Token") String token,
                                                               @RequestParam String plateNo,
                                                               @RequestParam String visitorName,
                                                               @RequestParam String visitorPhone,
                                                               @RequestParam Long communityId,
                                                               @RequestParam Long roomId,
                                                               @RequestParam String arriveTime,
                                                               @RequestParam String expireTime) {
        Long ownerId = validateOwnerToken(token);
        LocalDateTime arrive = LocalDateTime.parse(arriveTime);
        LocalDateTime expire = LocalDateTime.parse(expireTime);

        PropertyVisitorVehicle vv = new PropertyVisitorVehicle();
        vv.setOwnerId(ownerId);
        vv.setPlateNo(plateNo);
        vv.setVisitorName(visitorName);
        vv.setVisitorPhone(visitorPhone);
        vv.setCommunityId(communityId);
        vv.setRoomId(roomId);
        vv.setArriveTime(arrive);
        vv.setExpireTime(expire);
        vv.setStatus(1);
        visitorVehicleRepository.save(vv);
        log.info("Visitor vehicle created: owner={}, plate={}", ownerId, plateNo);
        return Result.ok(vv);
    }

    @GetMapping("/visitor-vehicles")
    public Result<List<PropertyVisitorVehicle>> myVisitorVehicles(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyVisitorVehicle> list = visitorVehicleRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId);
        return Result.ok(list);
    }

    @DeleteMapping("/visitor-vehicles/{id}")
    public Result<Void> cancelVisitorVehicle(@RequestHeader("X-Owner-Token") String token,
                                             @PathVariable Long id) {
        Long ownerId = validateOwnerToken(token);
        PropertyVisitorVehicle vv = visitorVehicleRepository.findById(id)
                .orElseThrow(() -> new BizException("访客车辆预约不存在"));
        if (!vv.getOwnerId().equals(ownerId)) {
            throw new BizException("无权操作");
        }
        vv.setStatus(0);
        visitorVehicleRepository.save(vv);
        log.info("Visitor vehicle cancelled: visitId={}, owner={}", id, ownerId);
        return Result.ok();
    }

    // ======================== 临时车 / 无感支付 ========================

    @GetMapping("/auto-pay/bindings")
    public Result<List<PropertyAutoPayBinding>> autoPayBindings(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyAutoPayBinding> list = autoPayBindingRepository.findByOwnerIdAndStatus(ownerId, 1);
        return Result.ok(list);
    }

    @PostMapping("/auto-pay/bind")
    public Result<PropertyAutoPayBinding> bindAutoPay(@RequestHeader("X-Owner-Token") String token,
                                                      @RequestParam String plateNo,
                                                      @RequestParam String payChannel) {
        Long ownerId = validateOwnerToken(token);
        PropertyAutoPayBinding binding = tempParkingService.bindAutoPay(ownerId, plateNo, payChannel);
        return Result.ok(binding);
    }

    @PostMapping("/auto-pay/unbind")
    public Result<Void> unbindAutoPay(@RequestHeader("X-Owner-Token") String token,
                                      @RequestParam String plateNo) {
        Long ownerId = validateOwnerToken(token);
        tempParkingService.unbindAutoPay(ownerId, plateNo);
        return Result.ok();
    }

    @GetMapping("/temp-parking/calculate")
    public Result<BigDecimal> calculateTempParkingFee(@RequestParam Long communityId,
                                                      @RequestParam Integer durationMinutes) {
        BigDecimal fee = tempParkingService.calculateFee(communityId, durationMinutes);
        return Result.ok(fee);
    }

    // ======================== 场地预约 ========================

    @GetMapping("/facilities")
    public Result<List<PropertyFacility>> availableFacilities(@RequestParam Long companyId) {
        List<PropertyFacility> list = facilityRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(companyId, 1);
        return Result.ok(list);
    }

    @GetMapping("/facilities/{id}/slots")
    public Result<List<String[]>> facilitySlots(@PathVariable Long id,
                                                 @RequestParam String date) {
        LocalDate d = LocalDate.parse(date);
        List<String[]> slots = reservationService.getAvailableSlots(id, d);
        return Result.ok(slots);
    }

    @PostMapping("/reservations")
    public Result<PropertyReservation> createReservation(@RequestHeader("X-Owner-Token") String token,
                                                          @RequestParam Long companyId,
                                                          @RequestParam Long facilityId,
                                                          @RequestParam String date,
                                                          @RequestParam String startTime,
                                                          @RequestParam String endTime,
                                                          @RequestParam(required = false) String remark) {
        Long ownerId = validateOwnerToken(token);
        LocalDate d = LocalDate.parse(date);
        LocalTime st = LocalTime.parse(startTime);
        LocalTime et = LocalTime.parse(endTime);
        PropertyReservation r = reservationService.createReservation(ownerId, companyId, facilityId, d, st, et, remark);
        return Result.ok(r);
    }

    @GetMapping("/reservations")
    public Result<List<PropertyReservation>> myReservations(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwnerToken(token);
        List<PropertyReservation> list = reservationRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId);
        return Result.ok(list);
    }

    @PostMapping("/reservations/{id}/cancel")
    public Result<Void> cancelReservation(@RequestHeader("X-Owner-Token") String token,
                                           @PathVariable Long id,
                                           @RequestParam(required = false) String reason) {
        Long ownerId = validateOwnerToken(token);
        // 校验归属权
        PropertyReservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new BizException("预约记录不存在"));
        if (!r.getOwnerId().equals(ownerId)) {
            throw new BizException("无权操作此预约");
        }
        reservationService.cancelReservation(id, reason);
        return Result.ok();
    }
}
