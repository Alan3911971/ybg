package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 业主端「抽盲盒 / 开盒结果」（/api/property/draw）。
 *
 * 设计稿 ㉕㉖：缴费即送 1 次、商城下单 +1，奖池 4 档概率公开，
 * 开盒结果必须同时给到「钱到哪了 / 边界在哪 / 凭证是什么」。
 *
 * 口径（都用真实数据，不编造）：
 * - 次数 earned = 已缴物业账单数（每笔 +1）+ 小区商城已支付订单数（每笔 +1）
 * - 次数 used   = property_draw_record 里该手机号的抽奖条数
 * - 概率       = prize.weight / sum(weight)，直接由物业后台配置的奖池算出并公开
 * - 奖品入账   = prizeType 3（余额）进物业钱包；其余写 user_coupon
 */
@Slf4j
@RestController
@RequestMapping("/api/property/draw")
@RequiredArgsConstructor
public class PropertyDrawController {

    private final PropertyPrizePoolRepository poolRepository;
    private final PropertyDrawRecordRepository recordRepository;
    private final UserCouponRepository couponRepository;
    private final PropertyWalletRepository walletRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final PropertyBindRelationRepository bindRelationRepository;
    private final PropertyBillRepository billRepository;
    private final IbigouOrderRepository orderRepository;
    private final PropertyDeductLogRepository deductLogRepository;
    private final MerchantPropertyBindingRepository bindingRepository;
    private final MerchantRepository merchantRepository;

    /** 奖品类型名（与 PropertyAdminController 校验一致：0普通 1折扣 2立减 3余额 4团购免单） */
    private static final String[] TYPE_NAME = {"谢谢参与", "折扣券", "立减券", "物业费抵扣券", "团购免单券"};
    private static final String[] TYPE_EMOJI = {"", "🎟️", "💵", "🧾", "👑"};

    private Long validateOwner(String token) {
        Long ownerId = PropertyOwnerController.TOKEN_STORE.get(token == null ? "" : token);
        if (ownerId == null) throw new BizException("登录已过期");
        return ownerId;
    }

    private Long ownerCompany(Long ownerId, Long companyId) {
        if (companyId != null) return companyId;
        return bindRelationRepository.findFirstByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1)
                .map(PropertyBindRelation::getCompanyId)
                .orElseThrow(() -> new BizException("请先在「我的」绑定物业公司"));
    }

    private String ownerPhone(Long ownerId) {
        return ownerRepository.findById(ownerId).map(PropertyOwner::getOwnerPhone)
                .orElseThrow(() -> new BizException("业主不存在"));
    }

    /**
     * ㉕ 抽盲盒首页：奖池 + 概率 + 剩余次数 + 三个统计。
     */
    @GetMapping("/pool")
    public Result<?> pool(@RequestHeader("X-Owner-Token") String token,
                          @RequestParam(required = false) Long companyId) {
        Long ownerId = validateOwner(token);
        Long cid = ownerCompany(ownerId, companyId);
        String phone = ownerPhone(ownerId);

        List<PropertyPrizePool> pool = poolRepository.findByCompanyIdOrderByChannelAscPoolTypeAscCreateTimeDesc(cid)
                .stream().filter(p -> p.getEnabled() != null && p.getEnabled() == 1).toList();
        int totalWeight = pool.stream().mapToInt(p -> p.getWeight() == null ? 0 : p.getWeight()).sum();

        List<Map<String, Object>> poolRows = new ArrayList<>();
        for (PropertyPrizePool p : pool) {
            int w = p.getWeight() == null ? 0 : p.getWeight();
            double prob = totalWeight <= 0 ? 0 : w * 100.0 / totalWeight;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("prizeId", p.getPrizeId());
            row.put("type", p.getPrizeType() == null ? 0 : p.getPrizeType());
            row.put("typeName", TYPE_NAME[Math.min(p.getPrizeType() == null ? 0 : p.getPrizeType(), 4)]);
            row.put("emoji", TYPE_EMOJI[Math.min(p.getPrizeType() == null ? 0 : p.getPrizeType(), 4)]);
            row.put("value", p.getPrizeValue());
            row.put("weight", w);
            row.put("prob", Math.round(prob * 10) / 10.0);
            row.put("remark", p.getRemark());
            row.put("toWallet", Integer.valueOf(3).equals(p.getPrizeType()));
            poolRows.add(row);
        }
        // 概率高的排前面，视觉上"最常中的在第一个"
        poolRows.sort((a, b) -> Double.compare((Double) b.get("prob"), (Double) a.get("prob")));

        // ---- 次数 ----
        List<Map<String, Object>> sources = new ArrayList<>();
        long billCnt = 0;
        try {
            billCnt = billRepository.findByOwnerIdAndCompanyIdAndStatusInOrderByDueDateAsc(
                    ownerId, cid, List.of(1, 2)).size();
        } catch (Exception ignore) { }
        if (billCnt > 0) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("label", "缴物业费 +1");
            s.put("count", (int) billCnt);
            sources.add(s);
        }
        long orderCnt = 0;
        try {
            orderCnt = orderRepository.findByUserPhoneOrderByCreateTimeDesc(phone).stream()
                    .filter(o -> o.getPayTime() != null).count();
        } catch (Exception ignore) { }
        if (orderCnt > 0) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("label", "商城下单 +1");
            s.put("count", (int) orderCnt);
            sources.add(s);
        }
        long used = recordRepository.countByPhone(phone);
        long earned = billCnt + orderCnt;
        long left = Math.max(0, earned - used);

        // ---- 统计 ----
        long todayDraws = recordRepository.countByCompanyAndDate(cid, LocalDate.now());
        YearMonth ym = YearMonth.now();
        BigDecimal monthDeduct = BigDecimal.ZERO;
        try {
            LocalDateTime from = ym.atDay(1).atStartOfDay();
            LocalDateTime to = ym.atEndOfMonth().plusDays(1).atStartOfDay();
            monthDeduct = deductLogRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId).stream()
                    .filter(d -> d.getCreateTime() != null && !d.getCreateTime().isBefore(from) && d.getCreateTime().isBefore(to))
                    .map(d -> d.getDeductAmount() == null ? BigDecimal.ZERO : d.getDeductAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception ignore) { }

        // 本期剩余天数：按自然月末
        int daysLeft = (int) (java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), ym.atEndOfMonth()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("times", Map.of("left", left, "earned", earned, "used", used, "sources", sources));
        data.put("pool", poolRows);
        data.put("poolTotal", pool.size());
        data.put("stats", Map.of(
                "todayDraws", todayDraws,
                "monthDeduct", monthDeduct.setScale(2, RoundingMode.HALF_UP),
                "ownerCost", "￥0"));
        data.put("daysLeft", daysLeft);
        data.put("probOpen", true);
        return Result.ok(data);
    }

    /**
     * ㉕ 抽奖：count=1 抽一次 / 2 连抽 / all 用完为止。
     */
    @PostMapping("/run")
    @Transactional
    public Result<?> run(@RequestHeader("X-Owner-Token") String token,
                         @RequestParam(required = false) Long companyId,
                         @RequestParam(defaultValue = "1") String count) {
        Long ownerId = validateOwner(token);
        Long cid = ownerCompany(ownerId, companyId);
        String phone = ownerPhone(ownerId);

        long used = recordRepository.countByPhone(phone);
        long billCnt;
        try {
            billCnt = billRepository.findByOwnerIdAndCompanyIdAndStatusInOrderByDueDateAsc(ownerId, cid, List.of(1, 2)).size();
        } catch (Exception e) { billCnt = 0; }
        long orderCnt;
        try {
            orderCnt = orderRepository.findByUserPhoneOrderByCreateTimeDesc(phone).stream()
                    .filter(o -> o.getPayTime() != null).count();
        } catch (Exception e) { orderCnt = 0; }
        long left = Math.max(0, billCnt + orderCnt - used);
        if (left <= 0) throw new BizException("没有可用次数了，缴物业费或商城下单都能再得 1 次");

        int n = "all".equalsIgnoreCase(count) ? (int) left
                : Math.max(1, Math.min((int) left, Integer.parseInt(count.matches("\\d+") ? count : "1")));

        List<PropertyPrizePool> pool = poolRepository.findByCompanyIdOrderByChannelAscPoolTypeAscCreateTimeDesc(cid)
                .stream().filter(p -> p.getEnabled() != null && p.getEnabled() == 1).toList();
        if (pool.isEmpty()) throw new BizException("本期奖池还未配置，请联系物业");
        int totalWeight = pool.stream().mapToInt(p -> p.getWeight() == null ? 0 : p.getWeight()).sum();
        if (totalWeight <= 0) throw new BizException("奖池权重配置异常");

        PropertyWallet wallet = walletRepository.findByOwnerIdAndCompanyId(ownerId, cid).orElse(null);
        BigDecimal balance = wallet == null ? BigDecimal.ZERO
                : (wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance());

        List<Map<String, Object>> results = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < n; i++) {
            PropertyPrizePool hit = spin(pool, totalWeight);
            int type = hit.getPrizeType() == null ? 0 : hit.getPrizeType();
            BigDecimal value = hit.getPrizeValue() == null ? BigDecimal.ZERO : hit.getPrizeValue();
            BigDecimal before = balance;
            BigDecimal after = before;
            Long couponId = null;

            if (type == 3) {                       // 余额 → 直接进物业钱包
                after = before.add(value);
                balance = after;
                if (wallet == null) {
                    wallet = new PropertyWallet();
                    wallet.setOwnerId(ownerId);
                    wallet.setCompanyId(cid);
                    wallet.setBalance(after);
                    wallet.setCreateTime(LocalDateTime.now());
                } else {
                    wallet.setBalance(after);
                }
                wallet.setUpdateTime(LocalDateTime.now());
                walletRepository.save(wallet);
                PropertyDeductLog dl = new PropertyDeductLog();
                dl.setOwnerId(ownerId);
                dl.setDeductAmount(value);
                dl.setSourceType(4);               // 4 = 抽奖入账
                dl.setBeforeBalance(before);
                dl.setAfterBalance(after);
                dl.setCreateTime(LocalDateTime.now());
                deductLogRepository.save(dl);
            }

            long seq = recordRepository.countByCompany(cid) + 1;
            String couponNo = String.format("BZ-%s-%04d", today.toString().replace("-", ""), seq);

            if (type > 0) {                        // 折扣 / 立减 / 免单 → 发券
                UserCoupon c = new UserCoupon();
                c.setUserPhone(phone);
                // 出资方：本小区已过审的特约商户（设计稿：奖池成本记在商户营销费里）
                c.setSourceMerchantNo(sponsorOf(cid));
                c.setPrizeType(type);
                c.setPrizeValue(value);
                c.setStatus(1);                    // 1 = 未使用
                c.setCanUseAfterDraw(0);
                c.setIsSupportIbigou(1);
                c.setVerifyType(0);
                c.setDrawBatchNo("P" + cid);
                c.setBizNo(couponNo);
                c.setValidStart(LocalDateTime.now());
                c.setValidEnd(LocalDateTime.now().plusMonths(12));
                c.setCreateTime(LocalDateTime.now());
                c.setUpdateTime(LocalDateTime.now());
                c = couponRepository.save(c);
                couponId = c.getCouponId();
            }

            PropertyDrawRecord rec = new PropertyDrawRecord();
            rec.setCompanyId(cid);
            rec.setOwnerId(ownerId);
            rec.setPhone(phone);
            rec.setPrizeId(hit.getPrizeId());
            rec.setPrizeType(type);
            rec.setPrizeValue(value);
            rec.setCouponNo(type > 0 ? couponNo : null);
            rec.setCouponId(couponId);
            rec.setWalletBefore(before);
            rec.setWalletAfter(after);
            rec.setSeq((int) seq);
            rec.setDrawDate(today);
            rec.setCreateTime(LocalDateTime.now());
            recordRepository.save(rec);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("seq", seq);
            r.put("type", type);
            r.put("typeName", TYPE_NAME[Math.min(type, 4)]);
            r.put("emoji", TYPE_EMOJI[Math.min(type, 4)]);
            r.put("value", value);
            r.put("couponNo", rec.getCouponNo());
            r.put("couponId", couponId);
            r.put("toWallet", type == 3);
            r.put("walletBefore", before.setScale(2, RoundingMode.HALF_UP));
            r.put("walletAfter", after.setScale(2, RoundingMode.HALF_UP));
            r.put("validEnd", LocalDateTime.now().plusMonths(12).toLocalDate().toString());
            r.put("usableNextMonth", true);
            results.add(r);
        }

        long nowLeft = Math.max(0, billCnt + orderCnt - recordRepository.countByPhone(phone));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("results", results);
        data.put("left", nowLeft);
        data.put("walletBalance", balance.setScale(2, RoundingMode.HALF_UP));
        return Result.ok(data);
    }

    /** 我的中奖记录 */
    @GetMapping("/records")
    public Result<?> records(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwner(token);
        String phone = ownerPhone(ownerId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PropertyDrawRecord r : recordRepository.findByPhoneOrderByRecordIdDesc(phone)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", r.getSeq());
            m.put("type", r.getPrizeType());
            m.put("typeName", TYPE_NAME[Math.min(r.getPrizeType() == null ? 0 : r.getPrizeType(), 4)]);
            m.put("emoji", TYPE_EMOJI[Math.min(r.getPrizeType() == null ? 0 : r.getPrizeType(), 4)]);
            m.put("value", r.getPrizeValue());
            m.put("couponNo", r.getCouponNo());
            m.put("date", r.getDrawDate() == null ? null : r.getDrawDate().toString());
            rows.add(m);
        }
        return Result.ok(Map.of("list", rows));
    }

    /** 本小区已过审的特约商户之一作为出奖方；没有绑定商户时记在物业名下（字段非空，不能留空） */
    private String sponsorOf(Long cid) {
        try {
            List<MerchantPropertyBinding> l = bindingRepository.findByCompanyIdAndStatus(cid, 1);
            if (l != null && !l.isEmpty()) {
                for (MerchantPropertyBinding b : l) {
                    if (b.getMerchantNo() != null && merchantRepository.findByMerchantNo(b.getMerchantNo()).isPresent()) {
                        return b.getMerchantNo();
                    }
                }
            }
        } catch (Exception ignore) { }
        return "PROPERTY";
    }

    private PropertyPrizePool spin(List<PropertyPrizePool> pool, int totalWeight) {
        int r = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int acc = 0;
        for (PropertyPrizePool p : pool) {
            acc += p.getWeight() == null ? 0 : p.getWeight();
            if (r <= acc) return p;
        }
        return pool.get(pool.size() - 1);
    }
}
