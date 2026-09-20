package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.MerchantPropertyBinding;
import com.ibigou.blindbox.entity.PropertyActivityAudit;
import com.ibigou.blindbox.entity.PropertyBindRelation;
import com.ibigou.blindbox.entity.PropertyOwner;
import com.ibigou.blindbox.entity.PropertySplitRecord;
import com.ibigou.blindbox.repository.MerchantPropertyBindingRepository;
import com.ibigou.blindbox.repository.MerchantRepository;
import com.ibigou.blindbox.repository.PropertyActivityAuditRepository;
import com.ibigou.blindbox.repository.PropertyBindRelationRepository;
import com.ibigou.blindbox.repository.PropertyOwnerRepository;
import com.ibigou.blindbox.repository.PropertySplitRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物业分账触发服务（双模式）：
 * <ul>
 *   <li><b>模式1 商家→物业</b>：消费者在商家消费 → 商家分账到关联物业公司 → 业主余额增加 → 抵扣物业费</li>
 *   <li><b>模式2 平台→物业</b>：消费者在宜必购平台消费 → 平台分账到关联物业公司 → 业主余额增加 → 抵扣物业费</li>
 * </ul>
 * 两种模式通过 merchant_property_binding.split_mode 区分，同一商家可同时启用。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PropertySplitTriggerService {

    /** 分账模式常量 */
    public static final int MODE_MERCHANT_TO_PROPERTY = 1;
    public static final int MODE_PLATFORM_TO_PROPERTY = 2;

    private final PropertyOwnerRepository ownerRepository;
    private final PropertyBindRelationRepository bindRelationRepository;
    private final PropertySplitRecordRepository splitRecordRepository;
    private final PropertyActivityAuditRepository activityAuditRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantPropertyBindingRepository merchantPropertyBindingRepository;

    // ==================== 模式1：商家→物业 ====================

    /**
     * 商家消费支付成功回调：查找该商家的「商家→物业」绑定，触发分账。
     * 由 OfflineOrderService.confirmPaid() 或商家支付回调调用。
     */
    @Transactional
    public void onMerchantOrderPaid(String orderNo, BigDecimal orderAmount, String merchantNo, String userPhone) {
        doSplit(orderNo, orderAmount, merchantNo, userPhone, MODE_MERCHANT_TO_PROPERTY);
    }

    // ==================== 模式2：平台→物业 ====================

    /**
     * 平台消费支付成功回调：查找该商家的「平台→物业」绑定，触发分账。
     * 由平台级支付回调（如盲盒抽奖支付）调用。
     */
    @Transactional
    public void onPlatformOrderPaid(String orderNo, BigDecimal orderAmount, String merchantNo, String userPhone) {
        doSplit(orderNo, orderAmount, merchantNo, userPhone, MODE_PLATFORM_TO_PROPERTY);
    }

    /**
     * 兼容旧接口：默认走商家→物业模式。
     */
    @Transactional
    public void onOrderPaid(String orderNo, BigDecimal orderAmount, String merchantNo, String userPhone) {
        onMerchantOrderPaid(orderNo, orderAmount, merchantNo, userPhone);
    }

    // ==================== 统一分账核心逻辑 ====================

    private void doSplit(String orderNo, BigDecimal orderAmount, String merchantNo, String userPhone, int splitMode) {
        // a. 查找业主（平台级会员，手机号即业主）
        PropertyOwner owner = ownerRepository.findByOwnerPhone(userPhone).orElse(null);
        if (owner == null || owner.getStatus() != 1) {
            return;
        }

        // b. 查找商家，校验分账开关
        Merchant merchant = merchantRepository.findById(merchantNo).orElse(null);
        if (merchant == null || merchant.getIsSplitEnabled() == null || merchant.getIsSplitEnabled() != 1
                || merchant.getSplitRatio() == null || merchant.getSplitRatio().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // c. 分账比例：商家全局 splitRatio
        BigDecimal effectiveRatio = merchant.getSplitRatio();

        // d. 分账目标：从业主绑定关系推导（会员绑定哪个物业就分账给哪个物业）
        List<PropertyBindRelation> binds = bindRelationRepository
                .findByOwnerIdAndStatusOrderByBindTimeAsc(owner.getOwnerId(), 1);
        if (binds.isEmpty()) {
            log.debug("Owner not bound to any property company: owner={}, phone={}", owner.getOwnerId(), userPhone);
            return;
        }
        Long companyId = binds.get(0).getCompanyId();

        // e. 计算分账金额
        BigDecimal splitAmount = orderAmount
                .multiply(effectiveRatio)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        if (splitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // f. 创建分账记录
        PropertySplitRecord record = new PropertySplitRecord();
        record.setOrderNo(orderNo);
        record.setOwnerId(owner.getOwnerId());
        record.setMerchantNo(merchantNo);
        record.setCompanyId(companyId);
        record.setOrderAmount(orderAmount);
        record.setSplitRatio(effectiveRatio);
        record.setSplitAmount(splitAmount);
        record.setSplitMode(splitMode);
        record.setSplitStatus(0); // PENDING
        splitRecordRepository.save(record);

        // g. 累加业主待结算金额
        owner.setPendingSplit(owner.getPendingSplit().add(splitAmount));
        ownerRepository.save(owner);

        // h. 日志
        String modeLabel = splitMode == MODE_MERCHANT_TO_PROPERTY ? "商家→物业" : "平台→物业";
        log.info("Split triggered [{}]: merchant={}, order={}, amount={}, split={}, owner={}, company={}",
                modeLabel, merchantNo, orderNo, orderAmount, splitAmount, owner.getOwnerId(), companyId);
    }

    // ==================== 退款冲正（双模式通用） ====================

    @Transactional
    public void onOrderRefunded(String orderNo) {
        PropertySplitRecord record = splitRecordRepository.findByOrderNo(orderNo).orElse(null);
        if (record == null || record.getSplitStatus() != 1) {
            return;
        }

        record.setSplitStatus(3); // REFUND_REVERSED
        splitRecordRepository.save(record);

        PropertyOwner owner = ownerRepository.findById(record.getOwnerId()).orElse(null);
        if (owner != null) {
            owner.setPendingSplit(owner.getPendingSplit().subtract(record.getSplitAmount()));
            ownerRepository.save(owner);
        }

        String modeLabel = record.getSplitMode() != null && record.getSplitMode() == MODE_PLATFORM_TO_PROPERTY
                ? "平台→物业" : "商家→物业";
        log.info("Split reversed [{}]: order={}, amount={}, owner={}",
                modeLabel, orderNo, record.getSplitAmount(), record.getOwnerId());
    }
}
