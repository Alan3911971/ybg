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
        return record(merchantNo, eventType, content, null);
    }

    /** 带手机号的播报记录（电视"最近中奖"展示手机尾号） */
    @Transactional
    public AnnounceLog record(String merchantNo, String eventType, String content, String phone) {
        return record(merchantNo, eventType, content, phone, null, null, null, null);
    }

    /**
     * 带结构化奖品信息的播报记录。
     * <p>电视大屏要按"中奖的内容"设计场景，光有一句自由文本不够：
     * prizeType/prizeValue 让前端直接拿到类型与数值（免掉正则猜），
     * customerSeq 拿"第X位顾客"，isPrimary 标记权威开奖记录（防重复弹窗）。</p>
     */
    @Transactional
    public AnnounceLog record(String merchantNo, String eventType, String content, String phone,
                               Integer prizeType, java.math.BigDecimal prizeValue,
                               Integer customerSeq, Integer isPrimary) {
        if (merchantNo == null || "PLATFORM".equals(merchantNo)) {
            return null; // 无门店上下文不播报
        }
        AnnounceLog log = new AnnounceLog();
        log.setMerchantNo(merchantNo);
        log.setEventType(eventType);
        log.setContent(content);
        log.setPhone(phone);
        log.setPrizeType(prizeType);
        log.setPrizeValue(prizeValue);
        log.setCustomerSeq(customerSeq);
        log.setIsPrimary(isPrimary == null ? 0 : isPrimary);
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
