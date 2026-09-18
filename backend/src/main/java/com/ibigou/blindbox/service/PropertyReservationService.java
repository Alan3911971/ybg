package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyFacility;
import com.ibigou.blindbox.entity.PropertyReservation;
import com.ibigou.blindbox.repository.PropertyFacilityRepository;
import com.ibigou.blindbox.repository.PropertyReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyReservationService {

    private final PropertyFacilityRepository facilityRepository;
    private final PropertyReservationRepository reservationRepository;

    /**
     * 创建预约：检查冲突 → 计算费用 → 生成编号 → 保存(status=0)
     */
    @Transactional
    public PropertyReservation createReservation(Long ownerId, Long companyId, Long facilityId,
                                                  LocalDate date, LocalTime startTime, LocalTime endTime,
                                                  String remark) {
        // 校验场地存在且启用
        PropertyFacility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new BizException("场地不存在"));
        if (facility.getStatus() == null || facility.getStatus() != 1) {
            throw new BizException("场地已停用");
        }
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

        // 计算费用：小时数 × 单价
        long minutes = Duration.between(startTime, endTime).toMinutes();
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal amount = hours.multiply(facility.getPrice()).setScale(2, RoundingMode.HALF_UP);

        // 生成预约编号
        String reservationNo = "RV" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        PropertyReservation reservation = new PropertyReservation();
        reservation.setReservationNo(reservationNo);
        reservation.setFacilityId(facilityId);
        reservation.setOwnerId(ownerId);
        reservation.setCompanyId(companyId);
        reservation.setReserveDate(date);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setAmount(amount);
        reservation.setPayStatus(0);
        reservation.setStatus(0);
        reservation.setRemark(remark);

        PropertyReservation saved = reservationRepository.save(reservation);
        log.info("Reservation created: no={}, facility={}, owner={}, date={} {}-{}",
                reservationNo, facilityId, ownerId, date, startTime, endTime);
        return saved;
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
