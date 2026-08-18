package com.ibigou.blindbox.service;

import com.ibigou.blindbox.entity.AnnounceLog;
import com.ibigou.blindbox.repository.AnnounceLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V1.5 P2 商家播报事件（蓝牙音箱/语音）。
 * 业务关键节点落播报话术；商家 H5 轮询新事件并语音朗读。
 * 硬件适配：当前为"事件记录 + 前端朗读"，后续可无缝替换为音箱服务端推送。
 */
@Service
@RequiredArgsConstructor
public class AnnounceService {

    private final AnnounceLogRepository announceRepository;

    @Transactional
    public AnnounceLog record(String merchantNo, String eventType, String content) {
        if (merchantNo == null || "PLATFORM".equals(merchantNo)) {
            return null; // 无门店上下文不播报
        }
        AnnounceLog log = new AnnounceLog();
        log.setMerchantNo(merchantNo);
        log.setEventType(eventType);
        log.setContent(content);
        log.setCreateTime(LocalDateTime.now());
        return announceRepository.save(log);
    }

    /** 全部播报记录（新→旧） */
    public List<AnnounceLog> list(String merchantNo) {
        List<AnnounceLog> list = new java.util.ArrayList<>(
                announceRepository.findByMerchantNoOrderByAnnounceIdAsc(merchantNo));
        java.util.Collections.reverse(list);
        return list;
    }

    /** 增量轮询：拉取 afterId 之后的新事件 */
    public List<AnnounceLog> poll(String merchantNo, Long afterId) {
        return announceRepository.findByMerchantNoAndAnnounceIdGreaterThanOrderByAnnounceIdAsc(
                merchantNo, afterId == null ? 0L : afterId);
    }
}
