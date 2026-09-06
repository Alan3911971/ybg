package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家盲盒配置服务（V1.4 5.2.1/5.2.2/5.2.7）。
 * <p>约束：第一层权重不可同时为 0；不允许全部大类禁用；公共投放为私有档位副本，
 * 下架公共档位不影响已发放用户券；专属盲盒独立奖品池不互通。</p>
 */
@Service
@RequiredArgsConstructor
public class MerchantConfigService {

    private final MerchantRepository merchantRepository;
    private final BoxPrizePoolRepository prizePoolRepository;
    private final BoxPublicPoolRepository publicPoolRepository;
    private final BoxGroupPrizePoolRepository groupPoolRepository;
    private final BoxGroupPoolConfigRepository groupPoolConfigRepository;
    private final com.ibigou.blindbox.service.AuditLogService auditLogService;

    /** 保存第一层权重 + 大类权重；两者不能同时为 0 */
    @Transactional
    public void saveWeights(String merchantNo, Integer privatePoolWeight, Integer publicPoolWeight,
                            Integer boxDiscountTotalWeight, Integer boxCouponTotalWeight) {
        Merchant m = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));
        int priv = privatePoolWeight == null ? 0 : privatePoolWeight;
        int pub = publicPoolWeight == null ? 0 : publicPoolWeight;
        if (priv <= 0 && pub <= 0) {
            throw new BizException("私有池权重与公共池权重不能同时为 0");
        }
        int discount = boxDiscountTotalWeight == null ? 0 : boxDiscountTotalWeight;
        int couponBalance = boxCouponTotalWeight == null ? 0 : boxCouponTotalWeight;
        if (discount <= 0 && couponBalance <= 0) {
            throw new BizException("折扣大类与立减余额大类权重不能同时为 0，防止抽奖死循环");
        }
        m.setPrivatePoolWeight(priv);
        m.setPublicPoolWeight(pub);
        m.setBoxDiscountTotalWeight(discount);
        m.setBoxCouponTotalWeight(couponBalance);
        m.setUpdateTime(LocalDateTime.now());
        merchantRepository.save(m);
    }

    /** 新增/编辑私有池档位（id 为 null 新增，否则编辑） */
    @Transactional
    public BoxPrizePool savePrizePool(String merchantNo, Long prizeId, Integer prizeType, BigDecimal prizeValue,
                                      Integer weight, Integer isPutPublic, Integer isSupportIbigou,
                                      Integer limitScope, Integer limitCycle, Integer limitMax, String remark,
                                      LocalDateTime expireTime) {
        validatePrize(prizeType, prizeValue, weight);
        BoxPrizePool p;
        if (prizeId == null) {
            p = new BoxPrizePool();
            p.setMerchantNo(merchantNo);
            p.setEnabled(1);
            p.setCreateTime(LocalDateTime.now());
        } else {
            p = prizePoolRepository.findById(prizeId)
                    .filter(x -> x.getMerchantNo().equals(merchantNo))
                    .orElseThrow(() -> new BizException("档位不存在或无权操作"));
        }
        p.setPrizeType(prizeType);
        p.setPrizeValue(prizeValue);
        p.setWeight(weight);
        p.setIsPutPublic(isPutPublic == null ? 0 : isPutPublic);
        p.setIsSupportIbigou(isSupportIbigou == null ? 0 : isSupportIbigou);
        p.setLimitScope(limitScope == null ? 1 : limitScope);
        p.setLimitCycle(limitCycle == null ? 1 : limitCycle);
        p.setLimitMax(limitMax == null ? 0 : limitMax);
        p.setRemark(remark);
        p.setExpireTime(expireTime);
        p.setUpdateTime(LocalDateTime.now());
        return prizePoolRepository.save(p);
    }

    @Transactional
    public void togglePrizePool(String merchantNo, Long prizeId, boolean enabled) {
        BoxPrizePool p = prizePoolRepository.findById(prizeId)
                .filter(x -> x.getMerchantNo().equals(merchantNo))
                .orElseThrow(() -> new BizException("档位不存在或无权操作"));
        p.setEnabled(enabled ? 1 : 0);
        p.setUpdateTime(LocalDateTime.now());
        prizePoolRepository.save(p);
    }

    @Transactional
    public void deletePrizePool(String merchantNo, Long prizeId) {
        BoxPrizePool p = prizePoolRepository.findById(prizeId)
                .filter(x -> x.getMerchantNo().equals(merchantNo))
                .orElseThrow(() -> new BizException("档位不存在或无权操作"));
        // 已发放到用户手里的券不受影响；仅删除档位配置
        prizePoolRepository.delete(p);
    }

    // ---------------- 5.2.2 公共投放 ----------------

    /** 投放：把私有档位复制到公共池（副本，继承 is_support_ibigou 等） */
    @Transactional
    public BoxPublicPool putPublic(String merchantNo, Long prizeId) {
        BoxPrizePool p = prizePoolRepository.findById(prizeId)
                .filter(x -> x.getMerchantNo().equals(merchantNo))
                .orElseThrow(() -> new BizException("档位不存在或无权操作"));
        BoxPublicPool pub = new BoxPublicPool();
        pub.setSourceMerchantNo(merchantNo);
        pub.setPrizeType(p.getPrizeType());
        pub.setPrizeValue(p.getPrizeValue());
        pub.setWeight(p.getWeight());
        pub.setEnabled(1);
        pub.setRemark(p.getRemark());
        pub.setIsSupportIbigou(p.getIsSupportIbigou());
        pub.setLimitScope(p.getLimitScope());
        pub.setLimitCycle(p.getLimitCycle());
        pub.setLimitMax(p.getLimitMax());
        pub.setCreateTime(LocalDateTime.now());
        pub.setUpdateTime(LocalDateTime.now());
        return publicPoolRepository.save(pub);
    }

    @Transactional
    public void togglePublic(String merchantNo, Long publicId, boolean enabled) {
        BoxPublicPool p = publicPoolRepository.findById(publicId)
                .filter(x -> x.getSourceMerchantNo().equals(merchantNo))
                .orElseThrow(() -> new BizException("公共档位不存在或无权操作"));
        p.setEnabled(enabled ? 1 : 0);
        p.setUpdateTime(LocalDateTime.now());
        publicPoolRepository.save(p);
    }

    // ---------------- 5.2.7 美团/饿了么专属盲盒 ----------------

    @Transactional
    public BoxGroupPrizePool saveGroupPool(String merchantNo, Integer channel, Long groupPoolId,
                                           Integer prizeType, BigDecimal prizeValue, Integer weight,
                                           Integer isSupportIbigou, Integer limitScope, Integer limitCycle,
                                           Integer limitMax) {
        if (channel == null || channel < 1 || channel > 3) {
            throw new BizException("渠道只能为美团(1)或饿了么(2)");
        }
        validatePrize(prizeType, prizeValue, weight);
        BoxGroupPrizePool p;
        if (groupPoolId == null) {
            p = new BoxGroupPrizePool();
            p.setMerchantNo(merchantNo);
            p.setChannel(channel);
            p.setEnabled(1);
            p.setCreateTime(LocalDateTime.now());
        } else {
            p = groupPoolRepository.findById(groupPoolId)
                    .filter(x -> x.getMerchantNo().equals(merchantNo))
                    .orElseThrow(() -> new BizException("专属档位不存在或无权操作"));
        }
        p.setPrizeType(prizeType);
        p.setPrizeValue(prizeValue);
        p.setWeight(weight);
        p.setIsSupportIbigou(isSupportIbigou == null ? 0 : isSupportIbigou);
        p.setLimitScope(limitScope == null ? 1 : limitScope);
        p.setLimitCycle(limitCycle == null ? 1 : limitCycle);
        p.setLimitMax(limitMax == null ? 0 : limitMax);
        p.setUpdateTime(LocalDateTime.now());
        return groupPoolRepository.save(p);
    }

    @Transactional
    public void toggleGroupPool(String merchantNo, Long groupPoolId, boolean enabled) {
        BoxGroupPrizePool p = groupPoolRepository.findById(groupPoolId)
                .filter(x -> x.getMerchantNo().equals(merchantNo))
                .orElseThrow(() -> new BizException("专属档位不存在或无权操作"));
        p.setEnabled(enabled ? 1 : 0);
        p.setUpdateTime(LocalDateTime.now());
        groupPoolRepository.save(p);
    }

    @Transactional
    public void deleteGroupPool(String merchantNo, Long groupPoolId) {
        BoxGroupPrizePool p = groupPoolRepository.findById(groupPoolId)
                .filter(x -> x.getMerchantNo().equals(merchantNo))
                .orElseThrow(() -> new BizException("专属档位不存在或无权操作"));
        groupPoolRepository.delete(p);
    }

    /** V1.5：门店抵扣与收款参数（百分比 0-100，0=本店禁止余额抵扣） */
    @Transactional
    public void saveDeductConfig(String merchantNo, Integer balanceDeductPercent,
                                 java.math.BigDecimal dailyDeductLimit,
                                 Integer receiveMode, String receiveQrImg) {
        Merchant m = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));
        if (balanceDeductPercent != null && (balanceDeductPercent < 0 || balanceDeductPercent > 100)) {
            throw new BizException("余额抵扣百分比范围必须为 0-100");
        }
        if (receiveMode != null && receiveMode != 1 && receiveMode != 2) {
            throw new BizException("收款模式只能为 1(模式A) 或 2(模式B)");
        }
        m.setBalanceDeductPercent(balanceDeductPercent);
        m.setDailyDeductLimit(dailyDeductLimit);
        m.setReceiveMode(receiveMode == null ? m.getReceiveMode() : receiveMode);
        m.setReceiveQrImg(receiveQrImg);
        m.setUpdateTime(LocalDateTime.now());
        merchantRepository.save(m);
        auditLogService.record(merchantNo, merchantNo, "deduct_config", null, null,
                "修改门店抵扣参数 percent=" + balanceDeductPercent + " dailyLimit=" + dailyDeductLimit,
                "NONE", null);
    }

    /** V1.5：专属盲盒大类权重（0/0 = 回退按档位权重直抽） */
    @Transactional
    public void saveGroupWeights(String merchantNo, Integer channel,
                                 Integer discountTotalWeight, Integer couponBalanceTotalWeight) {
        if (channel == null || channel < 1 || channel > 3) {
            throw new BizException("渠道只能为美团(1)或饿了么(2)");
        }
        BoxGroupPoolConfig cfg = groupPoolConfigRepository
                .findByMerchantNoAndChannel(merchantNo, channel).orElse(null);
        if (cfg == null) {
            cfg = new BoxGroupPoolConfig();
            cfg.setMerchantNo(merchantNo);
            cfg.setChannel(channel);
            cfg.setCreateTime(LocalDateTime.now());
        }
        cfg.setDiscountTotalWeight(discountTotalWeight == null ? 0 : discountTotalWeight);
        cfg.setCouponBalanceTotalWeight(couponBalanceTotalWeight == null ? 0 : couponBalanceTotalWeight);
        cfg.setUpdateTime(LocalDateTime.now());
        groupPoolConfigRepository.save(cfg);
    }

    private void validatePrize(Integer prizeType, BigDecimal prizeValue, Integer weight) {
        if (prizeType == null || prizeType < 1 || prizeType > 4) {
            throw new BizException("奖品类型不合法（1折扣 2立减 3普通余额 4团购免单余额）");
        }
        if (prizeValue == null || prizeValue.signum() <= 0) {
            throw new BizException("优惠数值必须大于 0");
        }
        if (prizeType == 1 && prizeValue.compareTo(BigDecimal.TEN) > 0) {
            throw new BizException("折扣券数值为折扣率（如 8 = 8折），范围 0-10");
        }
        if (weight == null || weight <= 0) {
            throw new BizException("档位权重必须大于 0");
        }
    }
}
