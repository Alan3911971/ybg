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
