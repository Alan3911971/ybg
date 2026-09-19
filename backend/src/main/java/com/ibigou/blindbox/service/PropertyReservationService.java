package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyCompany;
import com.ibigou.blindbox.entity.PropertyFacility;
import com.ibigou.blindbox.entity.PropertyReservation;
import com.ibigou.blindbox.repository.PropertyCompanyRepository;
import com.ibigou.blindbox.repository.PropertyFacilityRepository;
import com.ibigou.blindbox.repository.PropertyReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PropertyReservationService {

    private final PropertyFacilityRepository facilityRepository;
    private final PropertyReservationRepository reservationRepository;
    private final PropertyCompanyRepository companyRepository;
    private final GlobalConfigService configService;
    private final WxPayService wxPayService;
    private final AlipayService alipayService;

    public PropertyReservationService(PropertyFacilityRepository facilityRepository,
                                      PropertyReservationRepository reservationRepository,
                                      PropertyCompanyRepository companyRepository,
                                      GlobalConfigService configService,
                                      @Lazy WxPayService wxPayService,
                                      @Lazy AlipayService alipayService) {
        this.facilityRepository = facilityRepository;
        this.reservationRepository = reservationRepository;
        this.companyRepository = companyRepository;
        this.configService = configService;
        this.wxPayService = wxPayService;
        this.alipayService = alipayService;
    }

    /**
     * 业主预约（来源=1）
     */
    @Transactional
    public PropertyReservation createReservation(Long ownerId, Long companyId, Long facilityId,
                                                  LocalDate date, LocalTime startTime, LocalTime endTime,
                                                  String remark) {
        return doCreate(ownerId, 1, null, companyId, facilityId, date, startTime, endTime, remark);
    }

    /**
     * 商家预约（来源=2，自选物业公司，无需绑定）
     */
    @Transactional
    public PropertyReservation createMerchantReservation(String merchantNo, Long companyId, Long facilityId,
                                                          LocalDate date, LocalTime startTime, LocalTime endTime,
                                                          String remark) {
        return doCreate(0L, 2, merchantNo, companyId, facilityId, date, startTime, endTime, remark);
    }

    /**
     * 创建预约核心：校验场地/公司/开放时间/时段冲突 → 按公司收费开关计费。
     * 收费公司：amount=小时数×单价，payStatus=0（待支付），status=0（待确认）
     * 免费公司：amount=0，payStatus=1（无需支付），status=0（待确认，后台确认）
     */
    @Transactional
    public PropertyReservation doCreate(Long ownerId, Integer reservedType, String merchantNo,
                                        Long companyId, Long facilityId,
                                        LocalDate date, LocalTime startTime, LocalTime endTime,
                                        String remark) {
        // 校验场地存在且启用
        PropertyFacility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new BizException("场地不存在"));
        if (facility.getStatus() == null || facility.getStatus() != 1) {
            throw new BizException("场地已停用");
        }
        // 校验公司存在 + 收费开关
        PropertyCompany company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BizException("物业公司不存在"));
        boolean feeEnabled = company.getReservationFeeEnabled() != null && company.getReservationFeeEnabled() == 1;
        // 校验时间范围在开放时间内
        if (startTime.isBefore(facility.getOpenTime()) || endTime.isAfter(facility.getCloseTime())) {
            throw new BizException("预约时间超出场地开放时间（" + facility.getOpenTime() + " ~ " + facility.getCloseTime() + "）");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BizException("结束时间必须晚于开始时间");
        }

        // 冲突检测：同场地+同日期，排除已取消(3)的记录
        List<Integer> excludedStatuses = List.of(3);
        List<PropertyReservation> existing = reservationRepository
                .findByFacilityIdAndReserveDateAndStatusNotIn(facilityId, date, excludedStatuses);
        for (PropertyReservation r : existing) {
            // 时间段重叠判断：!(newEnd <= existStart || newStart >= existEnd)
            if (!(endTime.isBefore(r.getStartTime()) || endTime.equals(r.getStartTime())
                    || startTime.isAfter(r.getEndTime()) || startTime.equals(r.getEndTime()))) {
                throw new BizException("该时段已被预约，请选择其他时段");
            }
        }

        // 计算费用：收费公司 = 小时数 × 单价；免费公司 = 0
        BigDecimal amount = BigDecimal.ZERO;
        if (feeEnabled) {
            long minutes = Duration.between(startTime, endTime).toMinutes();
            BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            amount = hours.multiply(facility.getPrice()).setScale(2, RoundingMode.HALF_UP);
        }

        // 生成预约编号
        String reservationNo = "RV" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        PropertyReservation reservation = new PropertyReservation();
        reservation.setReservationNo(reservationNo);
        reservation.setFacilityId(facilityId);
        reservation.setOwnerId(ownerId);
        reservation.setReservedType(reservedType);
        reservation.setMerchantNo(merchantNo);
        reservation.setCompanyId(companyId);
        reservation.setReserveDate(date);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setAmount(amount);
        reservation.setPayStatus(feeEnabled && amount.signum() > 0 ? 0 : 1);
        reservation.setStatus(0);
        reservation.setRemark(remark);

        PropertyReservation saved = reservationRepository.save(reservation);
        log.info("Reservation created: no={}, facility={}, type={}, merchant={}, owner={}, date={} {}-{}, fee={}",
                reservationNo, facilityId, reservedType, merchantNo, ownerId, date, startTime, endTime, feeEnabled);
        return saved;
    }

    /**
     * 收费预约下单：生成 RS- 支付单并真实下单，返回二维码。
     */
    @Transactional
    public Map<String, Object> createPayOrder(Long reservationId, String channel) {
        if (!"wechat".equals(channel) && !"alipay".equals(channel)) {
            throw new BizException("不支持的支付方式");
        }
        PropertyReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("预约记录不存在"));
        if (r.getPayStatus() != null && r.getPayStatus() == 1) {
            throw new BizException("该预约已支付");
        }
        if (r.getAmount() == null || r.getAmount().signum() <= 0) {
            throw new BizException("该预约无需支付");
        }
        // 已下单未支付则复用原单号
        String paymentNo = r.getPaymentNo();
        if (paymentNo == null || paymentNo.isBlank()) {
            paymentNo = "RS" + System.currentTimeMillis() + (1000 + new Random().nextInt(9000));
            r.setPaymentNo(paymentNo);
            reservationRepository.save(r);
        }

        String notifyUrl = configService.get("IBIGOU_DOMAIN", "https://ybgtc.com");
        String desc = "场地预约-" + r.getReservationNo();
        Map<String, Object> result = new HashMap<>();
        result.put("paymentNo", paymentNo);
        result.put("amount", r.getAmount());
        result.put("reservationNo", r.getReservationNo());
        result.put("status", 0);
        if ("wechat".equals(channel)) {
            String qrCode = wxPayService.nativePay(paymentNo, r.getAmount(), desc, notifyUrl + "/api/pay/wx/notify");
            if (qrCode == null) {
                throw new BizException("微信支付下单失败，请稍后重试");
            }
            result.put("qrCode", qrCode);
        } else {
            String qrCode = alipayService.precreate(paymentNo, r.getAmount(), desc, notifyUrl + "/api/pay/alipay/notify");
            if (qrCode == null) {
                throw new BizException("支付宝支付下单失败，请稍后重试");
            }
            result.put("qrCode", qrCode);
        }
        log.info("预约支付单创建: paymentNo={}, reservation={}, amount={}, channel={}",
                paymentNo, r.getReservationNo(), r.getAmount(), channel);
        return result;
    }

    /**
     * 支付回调成功：预约 payStatus 0→1（幂等）。
     */
    @Transactional
    public void onPaymentSuccess(String paymentNo, String transactionId) {
        PropertyReservation r = reservationRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BizException("预约支付单不存在: " + paymentNo));
        if (r.getPayStatus() != null && r.getPayStatus() == 1) {
            log.info("预约支付已确认，忽略重复回调: paymentNo={}", paymentNo);
            return;
        }
        r.setPayStatus(1);
        reservationRepository.save(r);
        log.info("预约支付成功: paymentNo={}, reservation={}, tx={}", paymentNo, r.getReservationNo(), transactionId);
    }

    /**
     * 查询预约支付单状态（前端轮询）。
     */
    public Map<String, Object> queryStatus(String paymentNo) {
        PropertyReservation r = reservationRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BizException("支付单不存在"));
        Map<String, Object> m = new HashMap<>();
        m.put("paymentNo", paymentNo);
        m.put("paid", r.getPayStatus() != null && r.getPayStatus() == 1);
        m.put("payStatus", r.getPayStatus());
        m.put("amount", r.getAmount());
        m.put("status", r.getStatus());
        return m;
    }

    /**
     * 确认预约
     */
    @Transactional
    public void confirmReservation(Long reservationId) {
        PropertyReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("预约记录不存在"));
        if (r.getStatus() != 0) {
            throw new BizException("只有待确认的预约才能确认");
        }
        r.setStatus(1);
        reservationRepository.save(r);
        log.info("Reservation confirmed: id={}", reservationId);
    }

    /**
     * 取消预约
     */
    @Transactional
    public void cancelReservation(Long reservationId, String reason) {
        PropertyReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("预约记录不存在"));
        if (r.getStatus() == 3) {
            throw new BizException("预约已取消");
        }
        if (r.getStatus() == 4) {
            throw new BizException("已完成的预约不能取消");
        }
        r.setStatus(3);
        r.setCancelReason(reason);
        reservationRepository.save(r);
        log.info("Reservation cancelled: id={}, reason={}", reservationId, reason);
    }

    /**
     * 签到
     */
    @Transactional
    public void checkIn(Long reservationId) {
        PropertyReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("预约记录不存在"));
        if (r.getStatus() != 1) {
            throw new BizException("只有已确认的预约才能签到");
        }
        r.setStatus(2);
        reservationRepository.save(r);
        log.info("Reservation checked in: id={}", reservationId);
    }

    /**
     * 完成预约
     */
    @Transactional
    public void completeReservation(Long reservationId) {
        PropertyReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("预约记录不存在"));
        if (r.getStatus() != 2) {
            throw new BizException("只有已签到的预约才能完成");
        }
        r.setStatus(4);
        reservationRepository.save(r);
        log.info("Reservation completed: id={}", reservationId);
    }

    /**
     * 退款
     */
    @Transactional
    public void refundReservation(Long reservationId) {
        PropertyReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException("预约记录不存在"));
        r.setPayStatus(2);
        r.setStatus(3);
        reservationRepository.save(r);
        log.info("Reservation refunded: id={}", reservationId);
    }

    /**
     * 获取可用时段：基于已有预约返回空闲时间段列表
     * 返回格式：[{start:"HH:mm", end:"HH:mm"}, ...]
     */
    public List<String[]> getAvailableSlots(Long facilityId, LocalDate date) {
        PropertyFacility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new BizException("场地不存在"));

        // 查询当天有效预约（排除已取消）
        List<Integer> excludedStatuses = List.of(3);
        List<PropertyReservation> reservations = reservationRepository
                .findByFacilityIdAndReserveDateAndStatusNotIn(facilityId, date, excludedStatuses);

        // 按开始时间排序
        List<LocalTime> busyStarts = reservations.stream()
                .map(PropertyReservation::getStartTime)
                .sorted()
                .collect(Collectors.toList());
        List<LocalTime> busyEnds = reservations.stream()
                .map(PropertyReservation::getEndTime)
                .sorted()
                .collect(Collectors.toList());

        // 以1小时为粒度生成可用时段
        List<String[]> slots = new ArrayList<>();
        LocalTime cursor = facility.getOpenTime();
        LocalTime close = facility.getCloseTime();

        while (cursor.isBefore(close)) {
            LocalTime slotEnd = cursor.plusHours(1);
            if (slotEnd.isAfter(close)) {
                slotEnd = close;
            }
            // 检查此时段是否与任何已有预约冲突
            boolean conflict = false;
            for (int i = 0; i < reservations.size(); i++) {
                LocalTime rs = reservations.get(i).getStartTime();
                LocalTime re = reservations.get(i).getEndTime();
                if (!(slotEnd.isBefore(rs) || slotEnd.equals(rs)
                        || cursor.isAfter(re) || cursor.equals(re))) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) {
                slots.add(new String[]{cursor.toString(), slotEnd.toString()});
            }
            cursor = slotEnd;
        }
        return slots;
    }
}
