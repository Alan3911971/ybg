package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyAutoPayBinding;
import com.ibigou.blindbox.entity.PropertyTempParkingPayment;
import com.ibigou.blindbox.entity.PropertyVehicleLog;
import com.ibigou.blindbox.entity.PropertyVisitorVehicle;
import com.ibigou.blindbox.repository.PropertyAutoPayBindingRepository;
import com.ibigou.blindbox.repository.PropertyCompanyRepository;
import com.ibigou.blindbox.repository.PropertyTempParkingPaymentRepository;
import com.ibigou.blindbox.repository.PropertyVehicleLogRepository;
import com.ibigou.blindbox.repository.PropertyVisitorVehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 物业临时停车费计算与缴费服务。
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyTempParkingService {

    private final PropertyVehicleLogRepository vehicleLogRepository;
    private final PropertyTempParkingPaymentRepository tempParkingPaymentRepository;
    private final PropertyVisitorVehicleRepository visitorVehicleRepository;
    private final PropertyAutoPayBindingRepository autoPayBindingRepository;
    private final PropertyCompanyRepository companyRepository;

    /** 免费时长（分钟） */
    private static final int FREE_MINUTES = 30;
    /** 每小时费率（元） */
    private static final BigDecimal HOURLY_RATE = new BigDecimal("5.00");
    /** 每日封顶（元） */
    private static final BigDecimal DAILY_CAP = new BigDecimal("30.00");
    /** 一天分钟数 */
    private static final int MINUTES_PER_DAY = 1440;
    /** 默认分账比例 10% */
    private static final BigDecimal DEFAULT_SPLIT_RATIO = new BigDecimal("0.10");

    /**
     * 根据停车时长计算临时停车费。
     *
     * @param communityId     小区 ID（预留，后续可从全局配置读取差异化定价）
     * @param durationMinutes 停车时长（分钟）
     * @return 停车费，scale=2，HALF_UP
     */
    public BigDecimal calculateFee(Long communityId, int durationMinutes) {
        if (durationMinutes <= FREE_MINUTES) {
            return BigDecimal.ZERO;
        }

        int billableMinutes = durationMinutes - FREE_MINUTES;
        long hours = (long) Math.ceil(billableMinutes / 60.0);
        BigDecimal fee = HOURLY_RATE.multiply(new BigDecimal(hours));

        // 按天封顶：超过 1440 分钟时，天数 × 日封顶
        if (durationMinutes > MINUTES_PER_DAY) {
            long days = (long) Math.ceil((double) durationMinutes / MINUTES_PER_DAY);
            BigDecimal cap = DAILY_CAP.multiply(new BigDecimal(days));
            if (fee.compareTo(cap) > 0) {
                fee = cap;
            }
        } else {
            if (fee.compareTo(DAILY_CAP) > 0) {
                fee = DAILY_CAP;
            }
        }

        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 创建临时停车缴费记录。
     *
     * @param vehicleLogId 车辆通行记录 ID
     * @param plateNo      车牌号
     * @param communityId  小区 ID
     * @param payChannel   支付渠道
     * @return 缴费记录
     */
    @Transactional
    public PropertyTempParkingPayment createPayment(Long vehicleLogId, String plateNo, Long communityId, String payChannel) {
        PropertyVehicleLog vehicleLog = vehicleLogRepository.findById(vehicleLogId)
                .orElseThrow(() -> new BizException("车辆通行记录不存在"));

        if (vehicleLog.getEnterTime() == null) {
            throw new BizException("入场时间为空，无法计费");
        }

        // 检查是否存在有效的访客车辆预约（有效期内且状态正常 → 免费）
        Optional<PropertyVisitorVehicle> visitorOpt = visitorVehicleRepository
                .findByPlateNoAndStatusAndExpireTimeAfter(plateNo, 1, LocalDateTime.now());
        boolean visitorFree = visitorOpt.isPresent();

        // 计算停车时长
        LocalDateTime endTime = vehicleLog.getExitTime() != null ? vehicleLog.getExitTime() : LocalDateTime.now();
        long durationMinutes = Duration.between(vehicleLog.getEnterTime(), endTime).toMinutes();
        if (durationMinutes < 0) {
            durationMinutes = 0;
        }

        BigDecimal fee = visitorFree ? BigDecimal.ZERO : calculateFee(communityId, (int) durationMinutes);

        // 生成支付单号
        String paymentNo = "TP" + System.currentTimeMillis();

        PropertyTempParkingPayment payment = new PropertyTempParkingPayment();
        payment.setPaymentNo(paymentNo);
        payment.setVehicleLogId(vehicleLogId);
        payment.setPlateNo(plateNo);
        payment.setCommunityId(communityId);
        payment.setDurationMin((int) durationMinutes);
        payment.setFeeAmount(fee);
        payment.setPaidAmount(fee);
        payment.setPayChannel(payChannel);
        payment.setPayTime(LocalDateTime.now());
        payment.setStatus(0);

        tempParkingPaymentRepository.save(payment);

        log.info("Created temp parking payment: paymentNo={}, plateNo={}, fee={}, visitorFree={}",
                paymentNo, plateNo, fee, visitorFree);

        return payment;
    }

    /**
     * 支付成功回调处理。
     *
     * @param paymentNo      支付单号
     * @param wxTransactionId 微信支付交易号
     */
    @Transactional
    public void onPaymentSuccess(String paymentNo, String wxTransactionId) {
        PropertyTempParkingPayment payment = tempParkingPaymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BizException("缴费记录不存在"));

        if (payment.getStatus() != null && payment.getStatus() == 1) {
            log.warn("Payment already settled: paymentNo={}", paymentNo);
            return;
        }

        payment.setStatus(1);
        payment.setWxTransactionId(wxTransactionId);
        payment.setPayTime(LocalDateTime.now());

        // 临时停车分账（P1 阶段默认启用）
        boolean splitEnabled = true;
        if (splitEnabled) {
            BigDecimal splitAmount = payment.getPaidAmount()
                    .multiply(DEFAULT_SPLIT_RATIO)
                    .setScale(2, RoundingMode.HALF_UP);
            payment.setSplitAmount(splitAmount);
        }

        tempParkingPaymentRepository.save(payment);

        log.info("Temp parking payment success: paymentNo={}, wxTxId={}, splitAmount={}",
                paymentNo, wxTransactionId, payment.getSplitAmount());
    }

    /**
     * 检查指定车牌是否已绑定自动扣费。
     *
     * @param plateNo 车牌号
     * @return true=已绑定且有效
     */
    public boolean hasAutoPayBinding(String plateNo) {
        return autoPayBindingRepository.findByPlateNoAndStatus(plateNo, 1).isPresent();
    }

    /**
     * 绑定自动扣费。
     *
     * @param ownerId    业主 ID
     * @param plateNo    车牌号
     * @param payChannel 支付渠道
     * @return 绑定记录
     */
    @Transactional
    public PropertyAutoPayBinding bindAutoPay(Long ownerId, String plateNo, String payChannel) {
        Optional<PropertyAutoPayBinding> existing = autoPayBindingRepository.findByOwnerIdAndPlateNo(ownerId, plateNo);
        if (existing.isPresent() && existing.get().getStatus() != null && existing.get().getStatus() == 1) {
            throw new BizException("该车牌已绑定自动扣费，请勿重复操作");
        }

        PropertyAutoPayBinding binding = new PropertyAutoPayBinding();
        binding.setOwnerId(ownerId);
        binding.setPlateNo(plateNo);
        binding.setPayChannel(payChannel);
        binding.setStatus(1);
        binding.setSignTime(LocalDateTime.now());

        autoPayBindingRepository.save(binding);

        log.info("Auto-pay bound: ownerId={}, plateNo={}, payChannel={}", ownerId, plateNo, payChannel);

        return binding;
    }

    /**
     * 解绑自动扣费。
     *
     * @param ownerId 业主 ID
     * @param plateNo 车牌号
     */
    @Transactional
    public void unbindAutoPay(Long ownerId, String plateNo) {
        PropertyAutoPayBinding binding = autoPayBindingRepository.findByOwnerIdAndPlateNo(ownerId, plateNo)
                .orElseThrow(() -> new BizException("未找到对应的自动扣费绑定"));

        binding.setStatus(0);
        binding.setUnsignTime(LocalDateTime.now());

        autoPayBindingRepository.save(binding);

        log.info("Auto-pay unbound: ownerId={}, plateNo={}", ownerId, plateNo);
    }
}
