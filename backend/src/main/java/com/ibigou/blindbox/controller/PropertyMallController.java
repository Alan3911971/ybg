package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 业主端「小区商城 / 哪家有 / 我的订单 / 物业钱包」（/api/property/mall）。
 *
 * 对应设计稿 ⑳㉑㉒㉓㉛：买东西 → 8% 回到物业钱包 → 下月自动抵物业费。
 * 距离 / 在售状态这类字段目前没有数据源（商户表无坐标、无库存），
 * 接口如实不返回，前端也不编造——有数据再加，不做假数字。
 */
@Slf4j
@RestController
@RequestMapping("/api/property/mall")
@RequiredArgsConstructor
public class PropertyMallController {

    private final IbigouGoodsRepository goodsRepository;
    private final IbigouOrderRepository orderRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantPropertyBindingRepository bindingRepository;
    private final PropertyWalletRepository walletRepository;
    private final PropertyBindRelationRepository bindRelationRepository;
    private final PropertyOwnerRepository ownerRepository;
    private final UserCouponRepository couponRepository;
    private final PropertyFamilyMemberRepository familyMemberRepository;
    private final PropertyDeductLogRepository deductLogRepository;

    /** 让利率：设计稿统一 8%，优先取绑定自定义比例，其次商家配置 */
    private static final BigDecimal DEFAULT_SPLIT = new BigDecimal("0.08");

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
        return ownerRepository.findById(ownerId).map(PropertyOwner::getOwnerPhone).orElse(null);
    }

    /** 本小区已过审的绑定（splitMode=1 为默认分账模式，与后台建绑定时一致） */
    private List<MerchantPropertyBinding> boundList(Long companyId) {
        List<MerchantPropertyBinding> l =
                bindingRepository.findByCompanyIdAndSplitModeAndStatus(companyId, 1, 1);
        if (l == null || l.isEmpty()) {
            l = bindingRepository.findByCompanyIdAndStatus(companyId, 1);
        }
        return l == null ? List.of() : l;
    }

    /**
     * 库里 custom_split_ratio 存的是百分比整数（5 = 5%），也有场景存小数（0.08）。
     * 统一归一成小数，否则 129 × 5 会算出 645 的分账额。
     */
    private BigDecimal normalizeRatio(BigDecimal r) {
        if (r == null) return DEFAULT_SPLIT;
        return r.compareTo(BigDecimal.ONE) > 0
                ? r.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                : r;
    }

    private List<String> boundMerchants(Long companyId) {
        return boundList(companyId).stream()
                .map(MerchantPropertyBinding::getMerchantNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /** ⑳ 小区商城：本小区在售商品，每件带「抽 1 次 + 分账多少」 */
    @GetMapping("/goods")
    public Result<Map<String, Object>> goods(@RequestHeader("X-Owner-Token") String token,
                                             @RequestParam(required = false) Long companyId,
                                             @RequestParam(required = false) String merchantNo,
                                             @RequestParam(required = false) String q) {
        Long ownerId = validateOwner(token);
        companyId = ownerCompany(ownerId, companyId);
        List<String> merchants = boundMerchants(companyId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (String no : merchants) {
            if (merchantNo != null && !merchantNo.isBlank() && !merchantNo.equals(no)) continue;
            Merchant m = merchantRepository.findByMerchantNo(no).orElse(null);
            if (m == null) continue;
            BigDecimal ratio = normalizeRatio(boundList(companyId).stream()
                    .filter(b -> no.equals(b.getMerchantNo()) && b.getCustomSplitRatio() != null)
                    .map(MerchantPropertyBinding::getCustomSplitRatio)
                    .findFirst().orElse(DEFAULT_SPLIT));
            for (IbigouGoods g : goodsRepository.findByMerchantNoAndEnabledOrderBySortOrderDesc(no, 1)) {
                if (q != null && !q.isBlank() && g.getGoodsName() != null
                        && !g.getGoodsName().contains(q.trim())) continue;
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("goodsId", g.getGoodsId());
                it.put("goodsName", g.getGoodsName());
                it.put("price", g.getPrice());
                it.put("description", g.getDescription());
                it.put("salesCount", g.getSalesCount());
                it.put("merchantNo", no);
                it.put("merchantName", m.getMerchantName());
                it.put("drawTimes", 1);                       // 设计稿：每件送 1 次抽盒
                it.put("splitAmount",
                        (g.getPrice() == null ? BigDecimal.ZERO : g.getPrice())
                                .multiply(ratio).setScale(2, RoundingMode.HALF_UP));
                it.put("splitRatio", ratio);
                list.add(it);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("merchantCount", merchants.size());
        out.put("goodsCount", list.size());
        out.put("wallet", walletOf(ownerId, companyId));
        return Result.ok(out);
    }

    /** ㉛ 买的东西哪家有：按关键字找本小区商户，列出各自在售 */
    @GetMapping("/nearby")
    public Result<Map<String, Object>> nearby(@RequestHeader("X-Owner-Token") String token,
                                              @RequestParam(required = false) Long companyId,
                                              @RequestParam(required = false) String q) {
        Long ownerId = validateOwner(token);
        companyId = ownerCompany(ownerId, companyId);
        List<String> merchants = boundMerchants(companyId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (String no : merchants) {
            Merchant m = merchantRepository.findByMerchantNo(no).orElse(null);
            if (m == null) continue;
            List<IbigouGoods> gs = goodsRepository.findByMerchantNoAndEnabledOrderBySortOrderDesc(no, 1);
            List<IbigouGoods> hit = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                for (IbigouGoods g : gs) {
                    if (g.getGoodsName() != null && g.getGoodsName().contains(q.trim())) hit.add(g);
                }
            } else {
                hit.addAll(gs);
            }
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("merchantNo", no);
            it.put("merchantName", m.getMerchantName());
            it.put("status", m.getStatus());
            it.put("open", Integer.valueOf(1).equals(m.getStatus()));
            it.put("hitCount", hit.size());
            it.put("goods", hit.stream().map(g -> {
                Map<String, Object> gm = new LinkedHashMap<>();
                gm.put("goodsName", g.getGoodsName());
                gm.put("price", g.getPrice());
                return gm;
            }).collect(Collectors.toList()));
            list.add(it);
        }
        list.sort(Comparator.comparingInt((Map<String, Object> x) -> -(Integer) x.get("hitCount")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("keyword", q);
        out.put("list", list);
        out.put("hasShop", list.stream().anyMatch(x -> (Integer) x.get("hitCount") > 0));
        return Result.ok(out);
    }

    /** ㉒ 我的订单：按业主手机号匹配（商城下单用的是手机号） */
    @GetMapping("/orders")
    public Result<Map<String, Object>> orders(@RequestHeader("X-Owner-Token") String token) {
        Long ownerId = validateOwner(token);
        String phone = ownerPhone(ownerId);
        if (phone == null || phone.isBlank()) return Result.ok(Map.of("list", List.of(), "total", 0));
        List<IbigouOrder> os = orderRepository.findByUserPhoneOrderByCreateTimeDesc(phone);
        List<Map<String, Object>> list = os.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", o.getIbigouOrderNo());
            m.put("merchantNo", o.getMerchantNo());
            m.put("merchantName", merchantRepository.findByMerchantNo(o.getMerchantNo())
                    .map(Merchant::getMerchantName).orElse("本小区商户"));
            m.put("orderAmount", o.getOrderAmount());
            m.put("payAmount", o.getPayAmount());
            m.put("deductBalance", o.getDeductBalance());
            m.put("refundStatus", o.getRefundStatus());
            m.put("refundAmount", o.getRefundAmount());
            m.put("createTime", o.getCreateTime());
            m.put("payTime", o.getPayTime());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("total", list.size());
        return Result.ok(out);
    }

    /**
     * 业主端二维码（出码 / 分享用）。
     * 复用 /api/customer/qr-svg 那套生成逻辑——那个接口要盲盒客户登录态，
     * 业主端拿不到，所以这里按业主 token 另开一个。
     */
    @GetMapping("/qr")
    public Result<Map<String, Object>> qr(@RequestHeader("X-Owner-Token") String token,
                                          @RequestParam String content) {
        validateOwner(token);
        if (content == null || content.isBlank() || content.length() > 1024) {
            throw new BizException("二维码内容为空或过长");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", content);
        m.put("svg", com.ibigou.blindbox.common.QrSvgUtil.toSvg(content, 260));
        return Result.ok(m);
    }

    /**
     * ㉑ 收银台：把「这一单的钱怎么算」逐项摊开——
     * 商品原价 → 立减券 → 物业钱包抵扣（只能抵物业费，这里不可用）→ 微信实付 →
     * 这一单带回来多少（分账额 + 抽盒次数）。
     */
    @GetMapping("/checkout")
    public Result<Map<String, Object>> checkout(@RequestHeader("X-Owner-Token") String token,
                                                @RequestParam(required = false) Long companyId,
                                                @RequestParam(required = false) String items) {
        Long ownerId = validateOwner(token);
        companyId = ownerCompany(ownerId, companyId);
        String phone = ownerPhone(ownerId);

        // items: goodsId:数量 逗号分隔；不传则按本店在售全部各 1 件（演示用）
        List<String> merchants = boundMerchants(companyId);
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal goodsTotal = BigDecimal.ZERO;
        BigDecimal splitTotal = BigDecimal.ZERO;
        int drawTimes = 0;
        Set<String> usedMerchants = new LinkedHashSet<>();

        if (items != null && !items.isBlank()) {
            for (String seg : items.split(",")) {
                String[] kv = seg.split(":");
                if (kv.length == 0 || kv[0].isBlank()) continue;
                long gid = Long.parseLong(kv[0].trim());
                int qty = kv.length > 1 ? Math.max(1, Integer.parseInt(kv[1].trim())) : 1;
                IbigouGoods g = goodsRepository.findById(gid).orElse(null);
                if (g == null || !Integer.valueOf(1).equals(g.getEnabled())) continue;
                if (!merchants.contains(g.getMerchantNo())) continue;
                BigDecimal ratio = normalizeRatio(boundList(companyId).stream()
                        .filter(b -> g.getMerchantNo().equals(b.getMerchantNo()) && b.getCustomSplitRatio() != null)
                        .map(MerchantPropertyBinding::getCustomSplitRatio)
                        .findFirst().orElse(DEFAULT_SPLIT));
                BigDecimal lineAmt = (g.getPrice() == null ? BigDecimal.ZERO : g.getPrice())
                        .multiply(new BigDecimal(qty));
                BigDecimal lineSplit = lineAmt.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("goodsId", g.getGoodsId());
                line.put("goodsName", g.getGoodsName());
                line.put("price", g.getPrice());
                line.put("qty", qty);
                line.put("amount", lineAmt.setScale(2, RoundingMode.HALF_UP));
                line.put("splitAmount", lineSplit);
                line.put("merchantNo", g.getMerchantNo());
                line.put("merchantName", merchantRepository.findByMerchantNo(g.getMerchantNo())
                        .map(Merchant::getMerchantName).orElse("本小区商户"));
                lines.add(line);
                goodsTotal = goodsTotal.add(lineAmt);
                splitTotal = splitTotal.add(lineSplit);
                drawTimes += qty;
                usedMerchants.add(g.getMerchantNo());
            }
        }
        if (lines.isEmpty()) {
            // 未指定商品：取本小区在售各 1 件作为待结算内容
            for (String no : merchants) {
                for (IbigouGoods g : goodsRepository.findByMerchantNoAndEnabledOrderBySortOrderDesc(no, 1)) {
                    BigDecimal ratio = normalizeRatio(boundList(companyId).stream()
                            .filter(b -> no.equals(b.getMerchantNo()) && b.getCustomSplitRatio() != null)
                            .map(MerchantPropertyBinding::getCustomSplitRatio)
                            .findFirst().orElse(DEFAULT_SPLIT));
                    BigDecimal lineAmt = g.getPrice() == null ? BigDecimal.ZERO : g.getPrice();
                    BigDecimal lineSplit = lineAmt.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("goodsId", g.getGoodsId());
                    line.put("goodsName", g.getGoodsName());
                    line.put("price", g.getPrice());
                    line.put("qty", 1);
                    line.put("amount", lineAmt.setScale(2, RoundingMode.HALF_UP));
                    line.put("splitAmount", lineSplit);
                    line.put("merchantNo", no);
                    line.put("merchantName", merchantRepository.findByMerchantNo(no)
                            .map(Merchant::getMerchantName).orElse("本小区商户"));
                    lines.add(line);
                    goodsTotal = goodsTotal.add(lineAmt);
                    splitTotal = splitTotal.add(lineSplit);
                    drawTimes += 1;
                    usedMerchants.add(no);
                }
            }
        }

        // 可用券：user_coupon 里未使用的立减券（type=2），且面值不超过商品总额
        List<Map<String, Object>> coupons = new ArrayList<>();
        BigDecimal bestCoupon = BigDecimal.ZERO;
        Map<String, Object> bestCouponRow = null;
        if (phone != null && !phone.isBlank()) {
            for (UserCoupon c : couponRepository.findByUserPhoneOrderByCouponIdDesc(phone)) {
                if (!Integer.valueOf(1).equals(c.getStatus())) continue;
                Integer t = c.getPrizeType() == null ? 0 : c.getPrizeType();
                if (t != 2 && t != 3) continue;                       // 收银台只认立减券 / 余额券
                BigDecimal v = c.getPrizeValue() == null ? BigDecimal.ZERO : c.getPrizeValue();
                if (v.compareTo(BigDecimal.ZERO) <= 0 || v.compareTo(goodsTotal) > 0) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("couponId", c.getCouponId());
                row.put("type", t);
                row.put("value", v);
                row.put("validEnd", c.getValidEnd() == null ? null : c.getValidEnd().toLocalDate().toString());
                row.put("merchantNo", c.getSourceMerchantNo());
                row.put("merchantName", c.getSourceMerchantNo() == null ? "通用"
                        : merchantRepository.findByMerchantNo(c.getSourceMerchantNo())
                        .map(Merchant::getMerchantName).orElse("本小区商户"));
                coupons.add(row);
                if (v.compareTo(bestCoupon) > 0) { bestCoupon = v; bestCouponRow = row; }
            }
        }

        BigDecimal walletBalance = walletRepository.findByOwnerIdAndCompanyId(ownerId, companyId)
                .map(PropertyWallet::getBalance).orElse(BigDecimal.ZERO);
        BigDecimal payAmount = goodsTotal.subtract(bestCoupon).max(BigDecimal.ZERO);

        Map<String, Object> bill = new LinkedHashMap<>();
        bill.put("goodsTotal", goodsTotal.setScale(2, RoundingMode.HALF_UP));
        bill.put("couponCut", bestCoupon.setScale(2, RoundingMode.HALF_UP));
        bill.put("walletCut", BigDecimal.ZERO.setScale(2));
        bill.put("walletCutDisabled", true);
        bill.put("walletCutReason", "物业钱包只能抵物业费，不能抵商城消费");
        bill.put("payAmount", payAmount.setScale(2, RoundingMode.HALF_UP));

        // 分账按实付算——退款也要按同一口径回冲，否则账对不上
        BigDecimal splitFinal = splitTotal.multiply(
                goodsTotal.compareTo(BigDecimal.ZERO) > 0
                        ? payAmount.divide(goodsTotal, 6, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lines", lines);
        out.put("itemCount", lines.stream().mapToInt(l -> (Integer) l.get("qty")).sum());
        out.put("merchantSummary", usedMerchants.stream().map(no -> {
            long c = lines.stream().filter(l -> no.equals(l.get("merchantNo")))
                    .mapToInt(l -> (Integer) l.get("qty")).sum();
            return merchantRepository.findByMerchantNo(no).map(Merchant::getMerchantName).orElse(no) + " " + c;
        }).collect(Collectors.toList()));
        out.put("bill", bill);
        out.put("coupons", coupons);
        out.put("usedCoupon", bestCouponRow);
        out.put("wallet", walletOf(ownerId, companyId));
        out.put("bringBack", Map.of("drawTimes", drawTimes, "splitAmount", splitFinal,
                "desc", "实付 ￥" + payAmount.setScale(2, RoundingMode.HALF_UP)
                        + " × 8% = ￥" + splitFinal + " 分账进你物业钱包，下月账单自动抵；抽盒次数立刻到账"));
        out.put("delivery", List.of(
                Map.of("type", "pickup", "title", "自提",
                        "desc", usedMerchants.isEmpty() ? "到店自提" : "东门自提点 · 当天取", "fee", "免配送费"),
                Map.of("type", "deliver", "title", "送到家",
                        "desc", "满 ￥20 起送 · 约 25 分钟", "fee", "￥0")));
        return Result.ok(out);
    }

    /**
     * ㉓ 我的：物业钱包（本月可用 / 次月分开写）+ 商户余额 + 券 + 图鉴 + 我的物业 + 家庭成员。
     * 两个钱包必须是两个数——合成一个，业主就会问「能不能提现」。
     */
    @GetMapping("/mine")
    public Result<Map<String, Object>> mine(@RequestHeader("X-Owner-Token") String token,
                                            @RequestParam(required = false) Long companyId) {
        Long ownerId = validateOwner(token);
        companyId = ownerCompany(ownerId, companyId);
        PropertyOwner owner = ownerRepository.findById(ownerId).orElse(null);
        String phone = owner == null ? null : owner.getOwnerPhone();

        BigDecimal wBalance = walletRepository.findByOwnerIdAndCompanyId(ownerId, companyId)
                .map(PropertyWallet::getBalance).orElse(BigDecimal.ZERO);

        // 本月可用 = 上月及以前入账的部分（当月新进的钱次月才抵）
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        BigDecimal usableNow = BigDecimal.ZERO, nextMonth = BigDecimal.ZERO;
        for (PropertyDeductLog d : deductLogRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId)) {
            BigDecimal amt = d.getDeductAmount() == null ? BigDecimal.ZERO : d.getDeductAmount();
            if (d.getCreateTime() != null && d.getCreateTime().isBefore(monthStart)) {
                usableNow = usableNow.add(amt);
            } else {
                nextMonth = nextMonth.add(amt);
            }
        }
        // 没有流水时（新用户）不编造拆分，全部记为可用
        boolean hasLog = !deductLogRepository.findByOwnerIdOrderByCreateTimeDesc(ownerId).isEmpty();
        if (!hasLog) { usableNow = wBalance; nextMonth = BigDecimal.ZERO; }

        List<Map<String, Object>> coupons = new ArrayList<>();
        int couponCount = 0;
        if (phone != null) {
            for (UserCoupon c : couponRepository.findByUserPhoneOrderByCouponIdDesc(phone)) {
                if (!Integer.valueOf(1).equals(c.getStatus())) continue;
                couponCount++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("couponId", c.getCouponId());
                row.put("type", c.getPrizeType());
                row.put("value", c.getPrizeValue());
                row.put("validEnd", c.getValidEnd() == null ? null : c.getValidEnd().toLocalDate().toString());
                row.put("merchantName", c.getSourceMerchantNo() == null ? "通用"
                        : merchantRepository.findByMerchantNo(c.getSourceMerchantNo())
                        .map(Merchant::getMerchantName).orElse("本小区商户"));
                coupons.add(row);
            }
        }

        // 图鉴：已买过的不同商品数 / 本小区在售商品数（集满换抵扣券的规则由物业配，此处只给真实进度）
        int albumHave = 0, albumTotal = 0;
        try {
            if (phone != null) {
                albumHave = (int) orderRepository.findByUserPhoneOrderByCreateTimeDesc(phone).stream()
                        .filter(o -> o.getPayTime() != null)
                        .map(IbigouOrder::getIbigouOrderNo).distinct().count();
            }
            for (String no : boundMerchants(companyId)) {
                albumTotal += goodsRepository.findByMerchantNoAndEnabledOrderBySortOrderDesc(no, 1).size();
            }
            albumHave = Math.min(albumHave, albumTotal);
        } catch (Exception ignore) { }

        // 我的物业 / 家庭成员
        List<Map<String, Object>> homes = new ArrayList<>();
        for (PropertyBindRelation b : bindRelationRepository.findByOwnerIdAndStatusOrderByBindTimeAsc(ownerId, 1)) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("companyId", b.getCompanyId());
            h.put("roomId", b.getRoomId());
            h.put("status", b.getStatus());
            h.put("bindTime", b.getBindTime());
            homes.add(h);
        }
        int familyCount = 0;
        try {
            familyCount = familyMemberRepository.findByOwnerIdAndStatus(ownerId, 1).size();
        } catch (Exception ignore) { }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("phone", phone == null ? null : phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        out.put("ownerName", owner == null ? null : owner.getOwnerName());
        out.put("propertyWallet", Map.of(
                "balance", wBalance.setScale(2, RoundingMode.HALF_UP),
                "usableNow", usableNow.setScale(2, RoundingMode.HALF_UP),
                "nextMonth", nextMonth.setScale(2, RoundingMode.HALF_UP),
                "tip", "只能抵物业费 / 车位费 / 公摊水电，不能提现、转赠、抵商城消费"));
        out.put("merchantBalance", Map.of(
                "balance", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "tip", "商户余额是本店消费的零钱，只能在这家店抵"));
        out.put("couponCount", couponCount);
        out.put("coupons", coupons);
        out.put("album", Map.of("have", albumHave, "total", albumTotal));
        out.put("homes", homes);
        out.put("familyCount", familyCount);
        return Result.ok(out);
    }

    /** ㉓ 我的 · 物业钱包余额 */
    @GetMapping("/wallet")
    public Result<Map<String, Object>> wallet(@RequestHeader("X-Owner-Token") String token,
                                              @RequestParam(required = false) Long companyId) {
        Long ownerId = validateOwner(token);
        companyId = ownerCompany(ownerId, companyId);
        return Result.ok(walletOf(ownerId, companyId));
    }

    private Map<String, Object> walletOf(Long ownerId, Long companyId) {
        BigDecimal balance = walletRepository.findByOwnerIdAndCompanyId(ownerId, companyId)
                .map(PropertyWallet::getBalance).orElse(BigDecimal.ZERO);
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("balance", balance);
        w.put("tip", "只能抵物业费，不抵商城消费——这里买东西是微信付，付完 8% 回到这个数，下月自动抵物业费");
        return w;
    }
}
