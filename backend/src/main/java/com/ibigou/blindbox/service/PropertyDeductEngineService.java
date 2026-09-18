package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.PropertyBill;
import com.ibigou.blindbox.entity.PropertyBindRelation;
import com.ibigou.blindbox.entity.PropertyDeductLog;
import com.ibigou.blindbox.entity.PropertyOwner;
import com.ibigou.blindbox.entity.PropertyParkingFee;
import com.ibigou.blindbox.entity.PropertySplitRecord;
import com.ibigou.blindbox.repository.PropertyBillRepository;
import com.ibigou.blindbox.repository.PropertyBindRelationRepository;
import com.ibigou.blindbox.repository.PropertyDeductLogRepository;
import com.ibigou.blindbox.repository.PropertyOwnerRepository;
import com.ibigou.blindbox.repository.PropertyParkingFeeRepository;
import com.ibigou.blindbox.repository.PropertySplitRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * 物业扣款引擎服务。
 * <p>当分账记录结算（微信回调）时，按业主绑定顺序优先级将到账金额分配到账单和停车费。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyDeductEngineService {

    private final PropertySplitRecordRepository splitRecordRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyBindRelationRepository bindRelationRepository;
    private final PropertyBillRepository billRepository;
    private final PropertyParkingFeeRepository parkingFeeRepository;
    private final PropertyDeductLogRepository deductLogRepository;

    /** 未结清账单状态：0-PENDING, 1-PARTIAL, 3-OVERDUE */
    private static final List<Integer> UNPAID_STATUSES = Arrays.asList(0, 1, 3);

    /**
     * 分账结算后自动扣款入口（微信回调触发）。
     *
     * @param splitId 分账记录 ID
     */
    @Transactional
    public void onSplitSettled(Long splitId) {
        // a. 查找分账记录
        PropertySplitRecord split = splitRecordRepository.findById(splitId).orElse(null);
        if (split == null || split.getSplitStatus() == null || split.getSplitStatus() != 1) {
            return;
        }

        // b. 查找业主
        Long ownerId = split.getOwnerId();
        PropertyOwner owner = ownerRepository.findById(ownerId).orElse(null);
        if (owner == null) {
            return;
        }

        BigDecimal splitAmount = split.getSplitAmount();

        // c. pending → available
        owner.setPendingSplit(owner.getPendingSplit().subtract(splitAmount));
        owner.setBalance(owner.getBalance().add(splitAmount));

        // d. 获取有效绑定关系，按绑定时间升序
        List<PropertyBindRelation> binds = bindRelationRepository
                .findByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1);

        // e. 剩余待分配金额
        BigDecimal remaining = splitAmount;

        // f. 按绑定顺序逐个公司扣款
        for (PropertyBindRelation bind : binds) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            Long companyId = bind.getCompanyId();

            // --- 物业费账单扣款 ---
            List<PropertyBill> bills = billRepository
                    .findByOwnerIdAndCompanyIdAndStatusInOrderByDueDateAsc(ownerId, companyId, UNPAID_STATUSES);
            for (PropertyBill bill : bills) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal unpaid = bill.getAmount()
                        .subtract(nvl(bill.getDeducted()))
                        .subtract(nvl(bill.getPaid()));
                if (unpaid.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal deductAmt = remaining.min(unpaid);

                // 创建扣款日志
                PropertyDeductLog deductLog = new PropertyDeductLog();
                deductLog.setOwnerId(ownerId);
                deductLog.setBillId(bill.getBillId());
                deductLog.setSplitId(splitId);
                deductLog.setDeductAmount(deductAmt);
                deductLog.setSourceType(1); // CURRENT_SPLIT
                deductLog.setBeforeBalance(owner.getBalance());
                deductLog.setAfterBalance(owner.getBalance().subtract(deductAmt));
                deductLogRepository.save(deductLog);

                // 更新账单
                bill.setDeducted(nvl(bill.getDeducted()).add(deductAmt));
                if (nvl(bill.getDeducted()).add(nvl(bill.getPaid())).compareTo(bill.getAmount()) >= 0) {
                    bill.setStatus(2); // CLEARED
                } else {
                    bill.setStatus(1); // PARTIAL
                }
                billRepository.save(bill);

                // 更新余额与剩余
                owner.setBalance(owner.getBalance().subtract(deductAmt));
                remaining = remaining.subtract(deductAmt);
            }

            // --- 停车费账单扣款 ---
            List<PropertyParkingFee> parkingFees = parkingFeeRepository
                    .findByOwnerIdAndCompanyIdAndStatusInOrderByDueDateAsc(ownerId, companyId, UNPAID_STATUSES);
            for (PropertyParkingFee fee : parkingFees) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal unpaid = fee.getAmount()
                        .subtract(nvl(fee.getDeducted()))
                        .subtract(nvl(fee.getPaid()));
                if (unpaid.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal deductAmt = remaining.min(unpaid);

                // 创建扣款日志（billId 存 feeId）
                PropertyDeductLog deductLog = new PropertyDeductLog();
                deductLog.setOwnerId(ownerId);
                deductLog.setBillId(fee.getFeeId());
                deductLog.setSplitId(splitId);
                deductLog.setDeductAmount(deductAmt);
                deductLog.setSourceType(1); // CURRENT_SPLIT
                deductLog.setBeforeBalance(owner.getBalance());
                deductLog.setAfterBalance(owner.getBalance().subtract(deductAmt));
                deductLogRepository.save(deductLog);

                // 更新停车费
                fee.setDeducted(nvl(fee.getDeducted()).add(deductAmt));
                if (nvl(fee.getDeducted()).add(nvl(fee.getPaid())).compareTo(fee.getAmount()) >= 0) {
                    fee.setStatus(2); // CLEARED
                } else {
                    fee.setStatus(1); // PARTIAL
                }
                parkingFeeRepository.save(fee);

                // 更新余额与剩余
                owner.setBalance(owner.getBalance().subtract(deductAmt));
                remaining = remaining.subtract(deductAmt);
            }
        }

        // g. 保存业主最终状态
        ownerRepository.save(owner);

        // h. 日志
        log.info("Deducted {} from split {}, owner={}, remaining={}",
                splitAmount.subtract(remaining), splitId, ownerId, remaining);
    }

    /**
     * 物业管理员手动扣款。
     *
     * @param ownerId       业主 ID
     * @param billId        账单 ID（property_bill.billId）
     * @param amount        扣款金额
     * @param operatorRemark 操作备注
     */
    @Transactional
    public void manualDeduct(Long ownerId, Long billId, BigDecimal amount, String operatorRemark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("扣款金额必须大于 0");
        }

        // 校验业主及余额
        PropertyOwner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BizException("业主不存在"));
        if (owner.getBalance().compareTo(amount) < 0) {
            throw new BizException("业主余额不足");
        }

        // 查找账单
        PropertyBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new BizException("账单不存在"));

        BigDecimal unpaid = bill.getAmount()
                .subtract(nvl(bill.getDeducted()))
                .subtract(nvl(bill.getPaid()));
        if (unpaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("该账单已结清，无需扣款");
        }

        BigDecimal deductAmt = amount.min(unpaid);

        // 创建扣款日志
        PropertyDeductLog deductLog = new PropertyDeductLog();
        deductLog.setOwnerId(ownerId);
        deductLog.setBillId(billId);
        deductLog.setDeductAmount(deductAmt);
        deductLog.setSourceType(2); // MANUAL / CARRY_OVER
        deductLog.setBeforeBalance(owner.getBalance());
        deductLog.setAfterBalance(owner.getBalance().subtract(deductAmt));
        deductLogRepository.save(deductLog);

        // 更新账单
        bill.setDeducted(nvl(bill.getDeducted()).add(deductAmt));
        if (nvl(bill.getDeducted()).add(nvl(bill.getPaid())).compareTo(bill.getAmount()) >= 0) {
            bill.setStatus(2); // CLEARED
        } else {
            bill.setStatus(1); // PARTIAL
        }
        billRepository.save(bill);

        // 更新业主余额
        owner.setBalance(owner.getBalance().subtract(deductAmt));
        ownerRepository.save(owner);

        log.info("Manual deducted {} from owner={}, bill={}, remark={}",
                deductAmt, ownerId, billId, operatorRemark);
    }

    /**
     * 空值安全的 BigDecimal，返回 ZERO 代替 null。
     */
    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
