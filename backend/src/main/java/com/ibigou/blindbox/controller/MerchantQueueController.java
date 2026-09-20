package com.ibigou.blindbox.controller;

import com.ibigou.blindbox.common.Result;
import com.ibigou.blindbox.entity.*;
import com.ibigou.blindbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 商家端：排队叫号 + 广告管理
 */
@RestController
@RequestMapping("/api/merchant/queue")
@RequiredArgsConstructor
public class MerchantQueueController {

    private final MerchantQueueConfigRepository configRepository;
    private final MerchantQueueTicketRepository ticketRepository;
    private final MerchantAdRepository adRepository;

    // ---------- 排队配置 ----------

    @GetMapping("/config")
    public Result<MerchantQueueConfig> getConfig(@RequestParam String merchantNo) {
        return Result.ok(configRepository.findById(merchantNo).orElse(null));
    }

    @PostMapping("/config")
    public Result<MerchantQueueConfig> saveConfig(@RequestParam String merchantNo,
                                                  @RequestParam(required = false) String prefix,
                                                  @RequestParam(required = false) Integer windowCount,
                                                  @RequestParam(required = false) Integer estWaitMin,
                                                  @RequestParam(required = false) Integer enabled,
                                                  @RequestParam(required = false) String seatConfig) {
        MerchantQueueConfig cfg = configRepository.findById(merchantNo).orElse(new MerchantQueueConfig());
        if (cfg.getCreateTime() == null) {
            cfg.setMerchantNo(merchantNo);
            cfg.setCreateTime(LocalDateTime.now());
        }
        if (prefix != null) cfg.setPrefix(prefix);
        if (windowCount != null) cfg.setWindowCount(windowCount);
        if (estWaitMin != null) cfg.setEstWaitMin(estWaitMin);
        if (enabled != null) cfg.setEnabled(enabled);
        if (seatConfig != null) cfg.setSeatConfig(seatConfig);
        cfg.setUpdateTime(LocalDateTime.now());
        return Result.ok(configRepository.save(cfg));
    }

    // ---------- 叫号操作 ----------

    /** 叫下一位（按取号时间最早的等待号） */
    @PostMapping("/call-next")
    public Result<MerchantQueueTicket> callNext(@RequestParam String merchantNo) {
        MerchantQueueTicket t = ticketRepository
                .findFirstByMerchantNoAndStatusOrderByCreateTimeAsc(merchantNo, 0).orElse(null);
        if (t == null) return Result.fail("没有等待中的号");
        t.setStatus(1);
        t.setCallTime(LocalDateTime.now());
        ticketRepository.save(t);
        return Result.ok(t);
    }

    /** 手动叫指定号 */
    @PostMapping("/call")
    public Result<MerchantQueueTicket> call(@RequestParam String merchantNo, @RequestParam String ticketNo) {
        MerchantQueueTicket t = ticketRepository.findByMerchantNoAndTicketNo(merchantNo, ticketNo)
                .orElseThrow(() -> new RuntimeException("号不存在"));
        if (t.getStatus() == 2) return Result.fail("该号已完成");
        t.setStatus(1);
        t.setCallTime(LocalDateTime.now());
        ticketRepository.save(t);
        return Result.ok(t);
    }

    /** 完成（顾客到店服务完） */
    @PostMapping("/finish")
    public Result<Void> finish(@RequestParam String merchantNo, @RequestParam String ticketNo) {
        MerchantQueueTicket t = ticketRepository.findByMerchantNoAndTicketNo(merchantNo, ticketNo)
                .orElseThrow(() -> new RuntimeException("号不存在"));
        t.setStatus(2);
        t.setFinishTime(LocalDateTime.now());
        ticketRepository.save(t);
        return Result.ok();
    }

    /** 过号（排到队尾） */
    @PostMapping("/skip")
    public Result<Void> skip(@RequestParam String merchantNo, @RequestParam String ticketNo) {
        MerchantQueueTicket t = ticketRepository.findByMerchantNoAndTicketNo(merchantNo, ticketNo)
                .orElseThrow(() -> new RuntimeException("号不存在"));
        t.setStatus(3);
        ticketRepository.save(t);
        return Result.ok();
    }

    /** 当前队列列表 */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam String merchantNo) {
        Map<String, Object> m = new HashMap<>();
        m.put("calling", ticketRepository
                .findByMerchantNoAndStatusOrderByCreateTimeAsc(merchantNo, 1));
        m.put("waiting", ticketRepository
                .findByMerchantNoAndStatusOrderByCreateTimeAsc(merchantNo, 0));
        m.put("skipped", ticketRepository
                .findByMerchantNoAndStatusOrderByCreateTimeAsc(merchantNo, 3));
        return Result.ok(m);
    }

    // ---------- 广告管理 ----------

    @GetMapping("/ads")
    public Result<List<MerchantAd>> listAds(@RequestParam String merchantNo) {
        return Result.ok(adRepository.findByMerchantNoOrderBySortAscCreateTimeDesc(merchantNo));
    }

    @PostMapping("/ads")
    public Result<MerchantAd> createAd(@RequestParam String merchantNo,
                                       @RequestParam String title,
                                       @RequestParam String type,
                                       @RequestParam String url,
                                       @RequestParam(required = false, defaultValue = "0") Integer scope,
                                       @RequestParam(required = false) Integer durationSec,
                                       @RequestParam(required = false) Integer sort,
                                       @RequestParam(required = false) String startTime,
                                       @RequestParam(required = false) String endTime) {
        MerchantAd a = new MerchantAd();
        a.setMerchantNo(merchantNo);
        a.setTitle(title);
        a.setType(type);
        a.setUrl(url);
        a.setScope(scope);
        a.setDurationSec(durationSec != null ? durationSec : 10);
        a.setSort(sort != null ? sort : 0);
        a.setAuditStatus(scope == 1 ? 0 : 1); // 公共池待审，自家直接通过
        a.setEnabled(1);
        a.setStartTime(startTime != null ? LocalDateTime.parse(startTime) : null);
        a.setEndTime(endTime != null ? LocalDateTime.parse(endTime) : null);
        a.setCreateTime(LocalDateTime.now());
        a.setUpdateTime(LocalDateTime.now());
        return Result.ok(adRepository.save(a));
    }

    @PutMapping("/ads/{adId}")
    public Result<MerchantAd> updateAd(@PathVariable Long adId,
                                        @RequestParam(required = false) String title,
                                        @RequestParam(required = false) Integer enabled,
                                        @RequestParam(required = false) Integer sort,
                                        @RequestParam(required = false) Integer durationSec) {
        MerchantAd a = adRepository.findById(adId).orElseThrow(() -> new RuntimeException("广告不存在"));
        if (title != null) a.setTitle(title);
        if (enabled != null) a.setEnabled(enabled);
        if (sort != null) a.setSort(sort);
        if (durationSec != null) a.setDurationSec(durationSec);
        a.setUpdateTime(LocalDateTime.now());
        return Result.ok(adRepository.save(a));
    }

    @DeleteMapping("/ads/{adId}")
    public Result<Void> deleteAd(@PathVariable Long adId) {
        adRepository.deleteById(adId);
        return Result.ok();
    }
}
