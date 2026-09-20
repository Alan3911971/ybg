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
        // 当前叫号中
        MerchantQueueTicket calling = ticketRepository
                .findFirstByMerchantNoAndStatusOrderByCreateTimeAsc(merchantNo, 1).orElse(null);
        m.put("calling", calling);
        // 等待队列（按状态=0）
        List<MerchantQueueTicket> waiting = ticketRepository
                .findByMerchantNoAndStatusOrderByCreateTimeAsc(merchantNo, 0);
        m.put("waiting", waiting);
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

    private boolean inTimeRange(MerchantAd a) {
        LocalDateTime now = LocalDateTime.now();
        if (a.getStartTime() != null && now.isBefore(a.getStartTime())) return false;
        if (a.getEndTime() != null && now.isAfter(a.getEndTime())) return false;
        return true;
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
                                            @RequestParam(required = false) Integer windowNo) {
        MerchantQueueConfig cfg = configRepository.findById(merchantNo).orElse(null);
        if (cfg == null || cfg.getEnabled() != 1) {
            return Result.fail("排队未开启");
        }
        // 生成下一个号
        long count = ticketRepository.countByMerchantNoAndStatus(merchantNo, 0)
                + ticketRepository.countByMerchantNoAndStatus(merchantNo, 1);
        String prefix = cfg.getPrefix() != null ? cfg.getPrefix() : "A";
        String ticketNo = prefix + String.format("%03d", count + 1);
        // 检查是否已取号
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
        MerchantQueueTicket t = new MerchantQueueTicket();
        t.setMerchantNo(merchantNo);
        t.setTicketNo(ticketNo);
        t.setPhone(phone);
        t.setSeats(seats);
        t.setWindowNo(windowNo);
        t.setStatus(0);
        t.setCreateTime(LocalDateTime.now());
        ticketRepository.save(t);
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
