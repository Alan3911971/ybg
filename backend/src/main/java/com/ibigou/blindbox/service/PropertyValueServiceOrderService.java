package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyOwner;
import com.ibigou.blindbox.entity.PropertySplitRecord;
import com.ibigou.blindbox.entity.PropertyValueService;
import com.ibigou.blindbox.entity.PropertyVsOrder;
import com.ibigou.blindbox.repository.PropertyOwnerRepository;
import com.ibigou.blindbox.repository.PropertySplitRecordRepository;
import com.ibigou.blindbox.repository.PropertyValueServiceRepository;
import com.ibigou.blindbox.repository.PropertyVsOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 增值服务订单服务（维修/保洁/家政），含分账集成。
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyValueServiceOrderService {

    private final PropertyVsOrderRepository vsOrderRepository;
    private final PropertyValueServiceRepository valueServiceRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertySplitRecordRepository splitRecordRepository;

    /** 允许完成的状态：已支付(1)、进行中(2) */
    private static final List<Integer> COMPLETABLE_STATUSES = Arrays.asList(1, 2);

    /** 允许退款的状态：已支付(1)、进行中(2)、已完成(3) */
    private static final List<Integer> REFUNDABLE_STATUSES = Arrays.asList(1, 2, 3);

    /**
     * 创建增值服务订单。
     */
    @Transactional
    public PropertyVsOrder createOrder(Long ownerId, Long companyId, Long serviceId,
                                       Long roomId, Integer quantity, LocalDateTime appointmentTime) {
        PropertyValueService service = valueServiceRepository.findById(serviceId)
                .orElseThrow(() -> new BizException("增值服务不存在"));
        if (service.getStatus() == null || service.getStatus() != 1) {
            throw new BizException("该服务未上架");
        }

        BigDecimal totalAmount = service.getUnitPrice().multiply(BigDecimal.valueOf(quantity));

        PropertyVsOrder order = new PropertyVsOrder();
        order.setOrderNo("VS" + System.currentTimeMillis());
        order.setOwnerId(ownerId);
        order.setCompanyId(companyId);
        order.setServiceId(serviceId);
        order.setMerchantId(service.getMerchantId());
        order.setRoomId(roomId);
        order.setQuantity(quantity);
        order.setUnitPrice(service.getUnitPrice());
        order.setTotalAmount(totalAmount);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDeductAmount(BigDecimal.ZERO);
        order.setAppointmentTime(appointmentTime);
        order.setStatus(0); // 待支付

        vsOrderRepository.save(order);
        log.info("Created VS order: orderNo={}, ownerId={}, serviceId={}, totalAmount={}",
                order.getOrderNo(), ownerId, serviceId, totalAmount);
        return order;
    }

    /**
     * 余额支付：从业主余额扣减，不足部分走其他渠道（paidAmount = totalAmount - deductAmount）。
     */
    @Transactional
    public void payWithBalance(String orderNo, Long ownerId, BigDecimal deductAmount) {
        PropertyVsOrder order = vsOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (order.getStatus() != 0) {
            throw new BizException("订单状态不允许支付");
        }
        if (!order.getOwnerId().equals(ownerId)) {
            throw new BizException("订单不属于当前业主");
        }

        PropertyOwner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BizException("业主不存在"));
        if (owner.getBalance().compareTo(deductAmount) < 0) {
            throw new BizException("余额不足");
        }

        // 扣减业主余额
        owner.setBalance(owner.getBalance().subtract(deductAmount));
        ownerRepository.save(owner);

        // 更新订单
        order.setDeductAmount(deductAmount);
        order.setPaidAmount(order.getTotalAmount().subtract(deductAmount));
        order.setPayChannel("balance");
        order.setStatus(1); // 已支付
        vsOrderRepository.save(order);

        // 分账
        createSplitRecordIfNeeded(order);

        log.info("VS order paid with balance: orderNo={}, deductAmount={}, paidAmount={}",
                orderNo, deductAmount, order.getPaidAmount());
    }

    /**
     * 微信支付成功回调。
     */
    @Transactional
    public void onWechatPaySuccess(String orderNo, String wxTransactionId) {
        PropertyVsOrder order = vsOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (order.getStatus() != 0) {
            throw new BizException("订单状态不允许支付");
        }

        order.setWxTransactionId(wxTransactionId);
        order.setPayChannel("wechat");
        order.setPaidAmount(order.getTotalAmount());
        order.setStatus(1); // 已支付
        vsOrderRepository.save(order);

        // 分账
        createSplitRecordIfNeeded(order);

        log.info("VS order wechat pay success: orderNo={}, wxTxId={}", orderNo, wxTransactionId);
    }

    /**
     * 完成订单。
     */
    @Transactional
    public void completeOrder(String orderNo, String operatorName) {
        PropertyVsOrder order = vsOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (!COMPLETABLE_STATUSES.contains(order.getStatus())) {
            throw new BizException("订单状态不允许完成");
        }

        order.setStatus(3); // 已完成
        vsOrderRepository.save(order);

        log.info("VS order completed: orderNo={}, operator={}", orderNo, operatorName);
    }

    /**
     * 取消订单（仅待支付状态，且必须是本人）。
     */
    @Transactional
    public void cancelOrder(String orderNo, Long ownerId) {
        PropertyVsOrder order = vsOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (order.getStatus() != 0) {
            throw new BizException("订单状态不允许取消");
        }
        if (!order.getOwnerId().equals(ownerId)) {
            throw new BizException("订单不属于当前业主");
        }

        order.setStatus(4); // 已取消
        vsOrderRepository.save(order);

        log.info("VS order cancelled: orderNo={}, ownerId={}", orderNo, ownerId);
    }

    /**
     * 退款订单：若曾使用余额抵扣，退回至业主余额。
     */
    @Transactional
    public void refundOrder(String orderNo, String operatorName) {
        PropertyVsOrder order = vsOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException("订单不存在"));
        if (!REFUNDABLE_STATUSES.contains(order.getStatus())) {
            throw new BizException("订单状态不允许退款");
        }

        order.setStatus(5); // 已退款
        vsOrderRepository.save(order);

        // 退回余额
        if (order.getDeductAmount() != null && order.getDeductAmount().compareTo(BigDecimal.ZERO) > 0) {
            PropertyOwner owner = ownerRepository.findById(order.getOwnerId())
                    .orElseThrow(() -> new BizException("业主不存在"));
            owner.setBalance(owner.getBalance().add(order.getDeductAmount()));
            ownerRepository.save(owner);
            log.info("VS order refunded balance: orderNo={}, refundAmount={}", orderNo, order.getDeductAmount());
        }

        log.info("VS order refunded: orderNo={}, operator={}", orderNo, operatorName);
    }

    // ==================== 内部方法 ====================

    /**
     * 若服务配置了分账比例且关联商户，则创建分账记录。
     */
    private void createSplitRecordIfNeeded(PropertyVsOrder order) {
        if (order.getMerchantId() == null) {
            return;
        }
        PropertyValueService service = valueServiceRepository.findById(order.getServiceId()).orElse(null);
        if (service == null || service.getSplitRatio() == null
                || service.getSplitRatio().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal splitAmount = order.getTotalAmount()
                .multiply(service.getSplitRatio())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        PropertySplitRecord record = new PropertySplitRecord();
        record.setOrderNo(order.getOrderNo());
        record.setOwnerId(order.getOwnerId());
        record.setMerchantId(order.getMerchantId());
        record.setCompanyId(order.getCompanyId());
        record.setOrderAmount(order.getTotalAmount());
        record.setSplitRatio(service.getSplitRatio());
        record.setSplitAmount(splitAmount);
        record.setSplitStatus(0); // 待结算
        splitRecordRepository.save(record);

        log.info("Created split record: orderNo={}, merchantId={}, splitRatio={}%, splitAmount={}",
                order.getOrderNo(), order.getMerchantId(), service.getSplitRatio(), splitAmount);
    }
}
