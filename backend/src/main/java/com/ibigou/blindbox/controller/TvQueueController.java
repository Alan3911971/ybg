package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 电视排队叫号 + 广告 公开接口（顾客扫码/电视大屏）
 */
@RestController
@RequestMapping("/api/tv")
@RequiredArgsConstructor
public class TvQueueController {

    private final MerchantQueueConfigRepository configRepository;
    private final MerchantQueueTicketRepository ticketRepository;
    private final MerchantAdRepository adRepository;
    private final MerchantRepository merchantRepository;
    private final com.ibigou.blindbox.service.AnnounceService announceService;
    private final com.ibigou.blindbox.service.WxTemplateService wxTemplateService;

    @org.springframework.beans.factory.annotation.Value("${app.domain:https://ybgtc.com}")
    private String appDomain;

    /** 电视大屏：当前叫号 + 等待队列 + 广告 */
    @GetMapping("/{merchantNo}/screen")
    public Result<Map<String, Object>> screen(@PathVariable String merchantNo) {
        Map<String, Object> m = new HashMap<>();
        // 配置
        MerchantQueueConfig cfg = configRepository.findById(merchantNo).orElse(null);
        m.put("config", cfg);
        // 商家名
        Merchant merchant = merchantRepository.findById(merchantNo).orElse(null);
        m.put("merchantName", merchant != null ? merchant.getMerchantName() : merchantNo);
        // 大屏显示模式：call=叫号屏（默认） / activity=盲盒活动屏
        m.put("tvMode", cfg != null && cfg.getTvMode() != null ? cfg.getTvMode() : "call");
        // 当前叫号中
        MerchantQueueTicket calling = ticketRepository
                .findFirstByMerchantNoAndStatusOrderByCreateTimeAsc(merchantNo, 1).orElse(null);
        m.put("calling", calling);
        // 等待队列（按状态=0）
        List<MerchantQueueTicket> waiting = ticketRepository
                .findByMerchantNoAndStatusOrderByCreateTimeAsc(merchantNo, 0);
        m.put("waiting", waiting);
        // 最近中奖事件（用于电视弹动画）
        m.put("recentWins", recentWins(merchantNo));
        // 广告：自己家 enabled=1
        List<MerchantAd> ownAds = adRepository.findByMerchantNoOrderBySortAscCreateTimeDesc(merchantNo)
                .stream().filter(a -> a.getEnabled() == 1 && inTimeRange(a))
                .toList();
        // 广告：公共池 auditStatus=1
        List<MerchantAd> publicAds = adRepository
                .findByScopeAndAuditStatusAndEnabledOrderBySortAscCreateTimeDesc(1, 1, 1)
                .stream().filter(this::inTimeRange)
                .toList();
        List<Map<String, Object>> ads = new ArrayList<>();
        ownAds.forEach(a -> ads.add(adMap(a)));
        publicAds.forEach(a -> ads.add(adMap(a)));
        m.put("ads", ads);
        return Result.ok(m);
    }

    /**
     * 电视大屏取号二维码（PNG，免登录）——给 Android 原生层（ImageView 不支持 SVG）用。
     * 替换原来 APK 调外网 api.qrserver.com 的做法：不依赖外网、内容由服务端统一决定。
     */
    @GetMapping(value = "/{merchantNo}/qr.png", produces = "image/png")
    public void qrPng(@PathVariable String merchantNo, jakarta.servlet.http.HttpServletResponse resp)
            throws java.io.IOException {
        String data = tvQrTarget(merchantNo);
        byte[] png = com.ibigou.blindbox.common.QrSvgUtil.toPng(data, 300);
        resp.setContentType("image/png");
        resp.setContentLength(png.length);
        resp.getOutputStream().write(png);
    }

    /**
     * 电视大屏取号二维码（SVG，免登录）——网页版页面直接内联注入。
     * 真机（APK 原生层有码）时页面码由 JS 隐藏，因此不会双码。
     */
    @GetMapping(value = "/{merchantNo}/qr.svg", produces = "image/svg+xml")
    public String qrSvg(@PathVariable String merchantNo) {
        String data = tvQrTarget(merchantNo);
        return com.ibigou.blindbox.common.QrSvgUtil.toSvg(data, 260);
    }

    /**
     * 大屏二维码目标地址：按商家 tv_mode 决定。
     * activity=盲盒活动屏 -> 顾客端盲盒抽奖主页（扫码参与盲盒）；
     * call（默认）=叫号屏 -> 取号页。
     */
    private String tvQrTarget(String merchantNo) {
        MerchantQueueConfig cfg = configRepository.findById(merchantNo).orElse(null);
        String mode = cfg != null && cfg.getTvMode() != null ? cfg.getTvMode() : "call";
        String base = appDomain;
        if (base != null && base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base == null || base.isEmpty()) base = "https://ybgtc.com";
        if ("activity".equals(mode)) {
            return base + "/h5/customer/index.html?merchantNo=" + merchantNo;
        }
        return base + "/h5/queue.html?merchantNo=" + merchantNo;
    }

    private boolean inTimeRange(MerchantAd a) {        LocalDateTime now = LocalDateTime.now();
        if (a.getStartTime() != null && now.isBefore(a.getStartTime())) return false;
        if (a.getEndTime() != null && now.isAfter(a.getEndTime())) return false;
        return true;
    }

    /**
     * 最近中奖记录（电视右侧"最近中奖"列表 + 新中奖弹动画）。
     *
     * <p>只取 is_primary=1 的<b>权威开奖记录</b>：同一次抽奖会落多条 draw 记录
     * （后端开奖段 + 前端结算页触发 announce-prize），过去全取会让"最近中奖"列表
     * 出现同一次中奖的重复条目、弹屏也会重复弹。</p>
     *
     * <p>结构化输出 prizeType / prizeValue / customerSeq —— 大屏要按"中奖的内容"
     * 设计场景（免单 / 立减券 / 折扣券 / 余额各自一套舞台），靠正则猜类型会猜错。</p>
     */
    private List<Map<String, Object>> recentWins(String merchantNo) {
        return announceService.list(merchantNo).stream()
                .filter(l -> "draw".equals(l.getEventType()))
                .filter(l -> l.getIsPrimary() != null && l.getIsPrimary() == 1)
                .limit(4)
                .map(l -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("winId", l.getAnnounceId());
                    m.put("prizeType", l.getPrizeType());
                    m.put("prizeValue", l.getPrizeValue());
                    m.put("customerSeq", l.getCustomerSeq());
                    m.put("prizeName", prizeDisplayName(l.getPrizeType(), l.getPrizeValue(), l.getContent()));
                    String ph = l.getPhone();
                    m.put("phoneTail", ph != null && ph.length() >= 4 ? ph.substring(ph.length() - 4) : null);
                    return m;
                })
                .toList();
    }

    /**
     * 奖品展示名：优先按结构化字段生成（"立减5.00元券"/"9.00折优惠券"/"余额10.00元"），
     * 拿不到结构化字段时回退到解析自由文本（历史数据）。
     */
    private String prizeDisplayName(Integer prizeType, java.math.BigDecimal prizeValue, String content) {
        if (prizeType != null && prizeValue != null) {
            return switch (prizeType) {
                case 1 -> prizeValue.stripTrailingZeros().toPlainString() + "折优惠券";
                case 2 -> "立减" + prizeValue.stripTrailingZeros().toPlainString() + "元券";
                case 3 -> "余额" + prizeValue.stripTrailingZeros().toPlainString() + "元";
                case 4 -> "免单" + prizeValue.stripTrailingZeros().toPlainString() + "元";
                case 5 -> "谢谢参与";
                default -> "神秘礼品";
            };
        }
        return briefPrize(content);
    }

    /** 从中奖播报内容提取精简奖品名（如"恭喜您的盲盒开中了立减5.00元券。"→"立减5.00元券"） */
    private String briefPrize(String content) {
        if (content == null || content.isBlank()) return "大奖";
        String c = content;
        int idx = c.indexOf("开中了");
        if (idx >= 0) c = c.substring(idx + 3);
        else {
            idx = c.indexOf("开出");
            if (idx >= 0) c = c.substring(idx + 2);
        }
        int end = c.length();
        for (String sep : new String[]{"。", "，", "、", "！", "！"}) {
            int p = c.indexOf(sep);
            if (p >= 0 && p < end) end = p;
        }
        String prize = c.substring(0, end).trim();
        if (prize.length() > 18) prize = prize.substring(0, 18);
        return prize.isEmpty() ? "大奖" : prize;
    }

    private Map<String, Object> adMap(MerchantAd a) {
        Map<String, Object> m = new HashMap<>();
        m.put("adId", a.getAdId());
        m.put("type", a.getType());
        m.put("url", a.getUrl());
        m.put("title", a.getTitle());
        m.put("durationSec", a.getDurationSec());
        m.put("source", a.getMerchantNo());
        return m;
    }

    /** 顾客取号 */
    @PostMapping("/{merchantNo}/take")
    public Result<Map<String, Object>> take(@PathVariable String merchantNo,
                                            @RequestParam String phone,
                                            @RequestParam(required = false) Integer seats,
                                            @RequestParam(required = false) Integer windowNo,
                                            @RequestParam(required = false) String openid) {
        MerchantQueueConfig cfg = configRepository.findById(merchantNo).orElse(null);
        if (cfg == null || cfg.getEnabled() != 1) {
            return Result.fail("排队未开启");
        }
        // 检查是否已取号（同一手机号未完成时直接返回旧号，防重复取号产生双数据）
        List<MerchantQueueTicket> existing = ticketRepository
                .findByMerchantNoAndPhoneAndStatusInOrderByCreateTimeDesc(merchantNo, phone, List.of(0, 1));
        if (!existing.isEmpty()) {
            MerchantQueueTicket old = existing.get(0);
            Map<String, Object> m = new HashMap<>();
            m.put("ticketNo", old.getTicketNo());
            m.put("seats", old.getSeats());
            m.put("windowNo", old.getWindowNo());
            m.put("status", old.getStatus());
            long ahead = ticketRepository.countByMerchantNoAndStatus(merchantNo, 0);
            m.put("ahead", ahead);
            return Result.ok(m);
        }
        // 按人数/桌型分字母号段：2人桌=A、4人桌=B、6人桌=C、10人桌=D、12人桌=E（各自独立排号）
        String prefix = prefixBySeats(seats);
        // 生成号：该号段当前最大号 + 1（synchronized 防并发同毫秒撞号）
        String ticketNo;
        synchronized (("TVQ-" + merchantNo).intern()) {
            int seq = 0;
            Optional<MerchantQueueTicket> last = ticketRepository
                    .findFirstByMerchantNoAndTicketNoStartingWithOrderByTicketNoDesc(merchantNo, prefix);
            if (last.isPresent()) {
                seq = parseSeq(last.get().getTicketNo(), prefix);
            }
            ticketNo = prefix + String.format("%03d", seq + 1);
            MerchantQueueTicket t = new MerchantQueueTicket();
            t.setMerchantNo(merchantNo);
            t.setTicketNo(ticketNo);
            t.setPhone(phone);
            t.setSeats(seats);
            t.setWindowNo(windowNo);
            t.setStatus(0);
            t.setCreateTime(LocalDateTime.now());
            try {
                ticketRepository.save(t);
            } catch (Exception ex) {
                // 唯一索引冲突（极端并发）兜底：返回已存在的同号
                Optional<MerchantQueueTicket> dup = ticketRepository.findByMerchantNoAndTicketNo(merchantNo, ticketNo);
                if (dup.isPresent()) {
                    MerchantQueueTicket d = dup.get();
                    Map<String, Object> m = new HashMap<>();
                    m.put("ticketNo", d.getTicketNo());
                    m.put("seats", d.getSeats());
                    m.put("windowNo", d.getWindowNo());
                    m.put("status", d.getStatus());
                    m.put("ahead", ticketRepository.countByMerchantNoAndStatus(merchantNo, 0));
                    return Result.ok(m);
                }
                throw ex;
            }
        }
        // 绑定 openid
        if (openid != null && !openid.isBlank()) {
            wxTemplateService.bindOpenid(phone, openid);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("ticketNo", ticketNo);
        m.put("seats", seats);
        m.put("windowNo", windowNo);
        m.put("status", 0);
        long ahead = ticketRepository.countByMerchantNoAndStatus(merchantNo, 0);
        m.put("ahead", ahead);
        m.put("estWaitMin", cfg.getEstWaitMin());
        return Result.ok(m);
    }

    /** 桌型容量 → 字母号段（1-2人→A、3-4人→B、5-6人→C、7-10人→D、11-12人→E） */
    private String prefixBySeats(Integer seats) {
        if (seats == null) return "A";
        return switch (seats) {
            case 4 -> "B";
            case 6 -> "C";
            case 10 -> "D";
            case 12 -> "E";
            default -> "A";
        };
    }

    private int parseSeq(String ticketNo, String prefix) {
        try {
            return Integer.parseInt(ticketNo.substring(prefix.length()));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 顾客查排队进度 */
    @GetMapping("/{merchantNo}/my")
    public Result<Map<String, Object>> my(@PathVariable String merchantNo, @RequestParam String phone) {
        List<MerchantQueueTicket> list = ticketRepository
                .findByMerchantNoAndPhoneAndStatusInOrderByCreateTimeDesc(merchantNo, phone, List.of(0, 1));
        if (list.isEmpty()) return Result.fail("您还没有排队号");
        MerchantQueueTicket t = list.get(0);
        Map<String, Object> m = new HashMap<>();
        m.put("ticketNo", t.getTicketNo());
        m.put("seats", t.getSeats());
        m.put("windowNo", t.getWindowNo());
        m.put("status", t.getStatus());
        long ahead = ticketRepository.countByMerchantNoAndStatus(merchantNo, 0);
        m.put("ahead", ahead);
        return Result.ok(m);
    }
}
