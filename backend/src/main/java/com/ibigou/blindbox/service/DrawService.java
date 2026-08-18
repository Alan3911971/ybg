package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.enums.PoolType;
import com.ibigou.blindbox.enums.PrizeType;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 盲盒开奖服务。
 * <p>普通盲盒：第一层私有池/公共池按权重随机；私有池再按大类权重（折扣 vs 立减余额）随机档位。
 * 美团/饿了么专属盲盒：使用独立奖品池 box_group_prize_pool，不与私有/公共池互通。</p>
 * <p>约束：投放商家不能抽取自己投放的公共奖品；限额校验用档位行锁（并发防超限）；
 * 新奖品 can_use_after_draw=0，流程闭环后置 1。</p>
 */
@Service
@RequiredArgsConstructor
public class DrawService {

    private final MerchantRepository merchantRepository;
    private final BoxPrizePoolRepository prizePoolRepository;
    private final BoxPublicPoolRepository publicPoolRepository;
    private final BoxGroupPrizePoolRepository groupPoolRepository;
    private final BoxGroupPoolConfigRepository groupPoolConfigRepository;
    private final BoxPrizeLimitStatRepository statRepository;
    private final CouponService couponService;
    private final BalanceService balanceService;
    private final GlobalConfigService configService;
    private final OrderCalcService orderCalcService;
    private final MemberService memberService;
    private final AnnounceService announceService;

    /** 普通盲盒开奖 */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public DrawResult drawNormal(String merchantNo, String userPhone) {
        memberService.requireWriteAllowed(merchantNo);
        checkDailyParticipation(merchantNo, userPhone);
        Merchant merchant = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));
        if (merchant.getStatus() == null || merchant.getStatus() != 1) {
            throw new BizException("商家已停用");
        }
        checkConfigured(merchant);

        String batchNo = newBatchNo();
        // 第一层：私有池 vs 公共池（权重不可同时为 0，已由 checkConfigured 保证）
        // randomPick(left,right) true=left；left 传公共池权重，true 即走公共池
        boolean fromPublic = randomPick(merchant.getPublicPoolWeight(), merchant.getPrivatePoolWeight());

        if (fromPublic) {
            return drawFromPublic(merchantNo, userPhone, batchNo);
        }
        return drawFromPrivate(merchant, userPhone, batchNo);
    }

    /** 团购专属盲盒开奖（channel=1 美团 / 2 饿了么 / 3 抖音） */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public DrawResult drawGroup(String merchantNo, String userPhone, int channel) {
        memberService.requireWriteAllowed(merchantNo);
        checkDailyParticipation(merchantNo, userPhone);
        if (channel < 1 || channel > 3) {
            throw new BizException("未知团购渠道");
        }
        Merchant merchant = merchantRepository.findById(merchantNo)
                .orElseThrow(() -> new BizException("商家不存在"));
        if (merchant.getStatus() == null || merchant.getStatus() != 1) {
            throw new BizException("商家已停用");
        }
        List<BoxGroupPrizePool> pools = groupPoolRepository
                .findByMerchantNoAndChannelAndEnabled(merchantNo, channel, 1);
        if (pools.isEmpty()) {
            throw new BizException("本店未配置该渠道专属盲盒，请联系门店配置后参与");
        }
        String batchNo = newBatchNo();
        // V1.5：大类权重（折扣 vs 立减&余额）；0/0 回退按档位权重直抽
        BoxGroupPoolConfig cfg = groupPoolConfigRepository.findByMerchantNoAndChannel(merchantNo, channel).orElse(null);
        List<BoxGroupPrizePool> candidates = pools;
        if (cfg != null && (cfg.getDiscountTotalWeight() > 0 || cfg.getCouponBalanceTotalWeight() > 0)) {
            boolean discountCategory = randomPick(cfg.getDiscountTotalWeight(), cfg.getCouponBalanceTotalWeight());
            candidates = pools.stream()
                    .filter(p -> discountCategory == (p.getPrizeType() == PrizeType.DISCOUNT.getCode()))
                    .toList();
            if (candidates.isEmpty()) {
                candidates = pools; // 大类无档位时回退全部档位
            }
        }
        BoxGroupPrizePool prize = pickByWeight(candidates, BoxGroupPrizePool::getWeight, BoxGroupPrizePool::getGroupPoolId);
        checkLimit(prize.getGroupPoolId(), merchantNo, userPhone, prize.getLimitScope(),
                prize.getLimitCycle(), prize.getLimitMax());
        BoxGroupPrizePool locked = groupPoolRepository.findByIdForUpdate(prize.getGroupPoolId())
                .orElseThrow(() -> new BizException("奖品档位不存在"));
        recheckLimit(locked.getGroupPoolId(), merchantNo, userPhone, locked.getLimitScope(),
                locked.getLimitCycle(), locked.getLimitMax());

        int poolType = switch (channel) {
            case 1 -> PoolType.MEITUAN.getCode();
            case 2 -> PoolType.ELEME.getCode();
            default -> PoolType.DOUYIN.getCode();
        };
        DrawResult result = grantPrize(userPhone, merchantNo, locked.getPrizeType(),
                locked.getPrizeValue(), locked.getIsSupportIbigou(), poolType, batchNo, merchantNo);
        recordStat(merchantNo, userPhone, locked.getGroupPoolId(), poolType, locked.getPrizeType());
        announceDraw(merchantNo, userPhone, result, switch (channel) {
            case 1 -> "美团";
            case 2 -> "饿了么";
            default -> "抖音";
        });
        return result;
    }

    // ------------------------------------------------------------------ 内部

    private DrawResult drawFromPrivate(Merchant merchant, String userPhone, String batchNo) {
        List<BoxPrizePool> pools = prizePoolRepository
                .findByMerchantNoAndEnabled(merchant.getMerchantNo(), 1);
        if (pools.isEmpty()) {
            throw new BizException("本店盲盒活动尚未配置完成，请稍后再来");
        }
        int discountWeight = merchant.getBoxDiscountTotalWeight() == null ? 0 : merchant.getBoxDiscountTotalWeight();
        int couponBalanceWeight = merchant.getBoxCouponTotalWeight() == null ? 0 : merchant.getBoxCouponTotalWeight();
        if (discountWeight <= 0 && couponBalanceWeight <= 0) {
            throw new BizException("本店盲盒活动尚未配置完成，请稍后再来");
        }
        boolean discountCategory = randomPick(discountWeight, couponBalanceWeight);

        List<BoxPrizePool> candidates = pools.stream()
                .filter(p -> discountCategory == (p.getPrizeType() == PrizeType.DISCOUNT.getCode()))
                .toList();
        if (candidates.isEmpty()) {
            candidates = pools; // 大类无档位时回退全部档位（避免随机撞空档报"尚未配置"）
        }
        BoxPrizePool prize = pickByWeight(candidates, BoxPrizePool::getWeight, BoxPrizePool::getPrizeId);
        checkLimit(prize.getPrizeId(), merchant.getMerchantNo(), userPhone, prize.getLimitScope(),
                prize.getLimitCycle(), prize.getLimitMax());
        BoxPrizePool locked = prizePoolRepository.findByIdForUpdate(prize.getPrizeId())
                .orElseThrow(() -> new BizException("奖品档位不存在"));
        recheckLimit(locked.getPrizeId(), merchant.getMerchantNo(), userPhone, locked.getLimitScope(),
                locked.getLimitCycle(), locked.getLimitMax());

        DrawResult result = grantPrize(userPhone, merchant.getMerchantNo(), locked.getPrizeType(),
                locked.getPrizeValue(), locked.getIsSupportIbigou(), PoolType.PRIVATE.getCode(), batchNo,
                merchant.getMerchantNo());
        recordStat(merchant.getMerchantNo(), userPhone, locked.getPrizeId(), PoolType.PRIVATE.getCode(),
                locked.getPrizeType());
        announceDraw(merchant.getMerchantNo(), userPhone, result, "本店");
        return result;
    }

    private DrawResult drawFromPublic(String merchantNo, String userPhone, String batchNo) {
        List<BoxPublicPool> pools = publicPoolRepository
                .findByEnabledAndSourceMerchantNoNot(1, merchantNo);
        if (pools.isEmpty()) {
            throw new BizException("公共奖品池暂无可抽奖品，请稍后再来");
        }
        BoxPublicPool prize = pickByWeight(pools, BoxPublicPool::getWeight, BoxPublicPool::getPublicId);
        checkLimit(prize.getPublicId(), merchantNo, userPhone, prize.getLimitScope(),
                prize.getLimitCycle(), prize.getLimitMax());
        BoxPublicPool locked = publicPoolRepository.findByIdForUpdate(prize.getPublicId())
                .orElseThrow(() -> new BizException("奖品档位不存在"));
        recheckLimit(locked.getPublicId(), merchantNo, userPhone, locked.getLimitScope(),
                locked.getLimitCycle(), locked.getLimitMax());

        DrawResult result = grantPrize(userPhone, locked.getSourceMerchantNo(), locked.getPrizeType(),
                locked.getPrizeValue(), locked.getIsSupportIbigou(), PoolType.PUBLIC.getCode(), batchNo,
                merchantNo);
        recordStat(merchantNo, userPhone, locked.getPublicId(), PoolType.PUBLIC.getCode(), locked.getPrizeType());
        announceDraw(merchantNo, userPhone, result, "公共池");
        return result;
    }

    /** 发放资产：券入 user_coupon，余额入 user_balance_flow；全部 can_use_after_draw=0 */
    private DrawResult grantPrize(String userPhone, String sourceMerchantNo, int prizeType,
                                  BigDecimal prizeValue, int isSupportIbigou, int poolType, String batchNo,
                                  String drawMerchantNo) {
        if (prizeType == PrizeType.THANKS.getCode()) {
            // 谢谢参与：无资产，仅返回结果（安慰奖固定余额档位用 prizeType=3 走下方余额发放）
            return new DrawResult(batchNo, prizeType, prizeValue, false);
        }
        if (prizeType == PrizeType.BALANCE.getCode() || prizeType == PrizeType.GROUP_FREE.getCode()) {
            balanceService.grant(userPhone, drawMerchantNo, prizeValue, poolType, batchNo,
                    "盲盒抽奖发放余额(pool=" + poolType + ")");
            return new DrawResult(batchNo, prizeType, prizeValue, false);
        }
        couponService.grant(userPhone, sourceMerchantNo, prizeType, prizeValue, isSupportIbigou, batchNo);
        return new DrawResult(batchNo, prizeType, prizeValue, true);
    }

    /** 开奖播报（定稿话术：第X位用户/中奖/余额/抵扣比例/今日剩余额度） */
    private void announceDraw(String merchantNo, String userPhone, DrawResult result, String channel) {
        try {
            Merchant m = merchantRepository.findById(merchantNo).orElse(null);
            long seq = statRepository.countByMerchantNo(merchantNo);
            BigDecimal balance = balanceService.availableBalance(userPhone);
            int percent = m == null || m.getBalanceDeductPercent() == null
                    ? configService.balanceDeductRate() : m.getBalanceDeductPercent();
            String prizeTxt = result.isCoupon()
                    ? (result.prizeType() == 1 ? result.prizeValue() + "折" : "立减" + result.prizeValue() + "元")
                    : "余额" + result.prizeValue() + "元";
            BigDecimal dailyRemain = m == null ? BigDecimal.ZERO
                    : orderCalcService.remainingDailyQuota(userPhone, merchantNo, m);
            String content = "您好，这是本店第" + seq + "位参与盲盒活动的用户，恭喜您中奖，盲盒优惠" + prizeTxt
                    + "，您账户可用余额" + balance + "元，本店抵扣比例" + percent + "%"
                    + "，本店今日您余额最多还可以抵扣" + dailyRemain + "元，请告知收银员本单消费原价。";
            announceService.record(merchantNo, "draw", content);
        } catch (Exception e) {
            // 播报失败不影响主流程
        }
    }

    private void recordStat(String merchantNo, String userPhone, Long prizeId, int poolType, int prizeType) {
        BoxPrizeLimitStat stat = new BoxPrizeLimitStat();
        stat.setMerchantNo(merchantNo);
        stat.setUserPhone(userPhone);
        stat.setPrizeId(prizeId);
        stat.setPoolType(poolType);
        stat.setPrizeType(prizeType);
        // 一天一次防刷唯一键（并发双击第二个 INSERT 唯一冲突 → 事务回滚 → 转"今日已参与"）
        stat.setDailyKey(merchantNo + "|" + userPhone + "|" + LocalDate.now());
        stat.setCreateTime(LocalDateTime.now());
        try {
            statRepository.save(stat);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BizException("今日已参与过本店盲盒，请明天再来");
        }
    }

    private void checkConfigured(Merchant merchant) {
        int privateW = merchant.getPrivatePoolWeight() == null ? 0 : merchant.getPrivatePoolWeight();
        int publicW = merchant.getPublicPoolWeight() == null ? 0 : merchant.getPublicPoolWeight();
        if (privateW <= 0 && publicW <= 0) {
            throw new BizException("本店盲盒活动尚未配置完成，请稍后再来");
        }
    }

    /** 限额预检（无锁，仅提前拦截） */
    private void checkLimit(Long prizeId, String merchantNo, String userPhone,
                            Integer scope, Integer cycle, Integer max) {
        if (max == null || max <= 0) {
            return; // 0 不限
        }
        if (countInWindow(prizeId, merchantNo, userPhone, scope, cycle) >= max) {
            throw new BizException("该奖品已达中奖上限");
        }
    }

    /** 限额重检（档位行锁内，并发安全） */
    private void recheckLimit(Long prizeId, String merchantNo, String userPhone,
                              Integer scope, Integer cycle, Integer max) {
        if (max == null || max <= 0) {
            return;
        }
        long counted = countInWindow(prizeId, merchantNo, userPhone, scope, cycle);
        if (counted >= max) {
            throw new BizException("该奖品已达中奖上限");
        }
    }

    /** 一天一次：同一顾客在同一个商家（含所有渠道二维码）每天只能参与一次盲盒 */
    private void checkDailyParticipation(String merchantNo, String userPhone) {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long participated = statRepository.countByMerchantNoAndUserPhoneAndCreateTimeAfter(
                merchantNo, userPhone, todayStart);
        if (participated > 0) {
            throw new BizException("今日已参与过本店盲盒，请明天再来");
        }
    }

    private long countInWindow(Long prizeId, String merchantNo, String userPhone,
                               Integer scope, Integer cycle) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = cycleStart(cycle == null ? 3 : cycle, now);
        LocalDateTime until = now.plusSeconds(1);
        int sc = scope == null ? 1 : scope;
        return switch (sc) {
            case 2 -> statRepository.countByPrizeAndMerchant(prizeId, merchantNo, since, until);
            case 3 -> statRepository.countByPrizeAndUser(prizeId, userPhone, since, until);
            default -> statRepository.countByPrize(prizeId, since, until);
        };
    }

    private LocalDateTime cycleStart(int cycle, LocalDateTime now) {
        return switch (cycle) {
            case 1 -> now.toLocalDate().atStartOfDay();
            case 2 -> now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            default -> LocalDateTime.of(1970, 1, 1, 0, 0);
        };
    }

    /** 权重随机：leftWeight/rightWeight，返回 true=命中左侧（leftWeight 区间） */
    private boolean randomPick(int leftWeight, int rightWeight) {
        long total = (long) leftWeight + rightWeight; // long 防 int 溢出（P3）
        if (total <= 0) {
            throw new BizException("权重配置错误");
        }
        return ThreadLocalRandom.current().nextLong(total) < leftWeight;
    }

    /** 按权重随机选档位（weight<=0 的档位不会被选中） */
    private <T> T pickByWeight(List<T> items, java.util.function.ToIntFunction<T> weightFn,
                               java.util.function.ToLongFunction<T> idFn) {
        long total = items.stream().mapToLong(i -> Math.max(0, weightFn.applyAsInt(i))).sum();
        if (total <= 0) {
            throw new BizException("奖品权重配置错误");
        }
        long r = ThreadLocalRandom.current().nextLong(total);
        long acc = 0;
        for (T item : items) {
            acc += Math.max(0, weightFn.applyAsInt(item));
            if (r < acc) {
                return item;
            }
        }
        return items.get(items.size() - 1);
    }

    private String newBatchNo() {
        return "DBX" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
