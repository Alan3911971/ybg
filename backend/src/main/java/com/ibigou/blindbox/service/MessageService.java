package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.Merchant;
import com.ibigou.blindbox.entity.MerchantMessage;
import com.ibigou.blindbox.repository.MerchantMessageRepository;
import com.ibigou.blindbox.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * V1.5 P2 商家站内消息：会员到期提醒（7/3/1 天前，幂等去重）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private static final int[] REMIND_DAYS = {7, 3, 1};

    private final MerchantMessageRepository messageRepository;
    private final MerchantRepository merchantRepository;

    public List<MerchantMessage> list(String merchantNo) {
        return messageRepository.findByMerchantNoOrderByIdDesc(merchantNo);
    }

    public long unreadCount(String merchantNo) {
        return messageRepository.countByMerchantNoAndIsRead(merchantNo, 0);
    }

    @Transactional
    public void markRead(Long id, String merchantNo) {
        MerchantMessage msg = messageRepository.findById(id)
                .filter(m -> m.getMerchantNo().equals(merchantNo))
                .orElseThrow(() -> new BizException("消息不存在"));
        msg.setIsRead(1);
        messageRepository.save(msg);
    }

    @Transactional
    public void markAllRead(String merchantNo) {
        for (MerchantMessage m : messageRepository.findByMerchantNoOrderByIdDesc(merchantNo)) {
            if (m.getIsRead() == 0) {
                m.setIsRead(1);
                messageRepository.save(m);
            }
        }
    }

    /** 到期提醒生成（每日定时；按 商家+类型+日期 幂等） */
    @Transactional
    public void generateExpireReminders() {
        LocalDateTime now = LocalDateTime.now();
        String today = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int count = 0;
        for (Merchant m : merchantRepository.findAll()) {
            if (m.getMemberExpireTime() == null) {
                continue;
            }
            long days = Duration.between(now, m.getMemberExpireTime()).toDays();
            if (days < 0) {
                continue; // 已过期不提醒（写锁定已拦截）
            }
            for (int d : REMIND_DAYS) {
                if (days <= d && days > d - 1) {
                    String key = today + "-" + d;
                    if (messageRepository.countExist(m.getMerchantNo(), "expire_remind", key) == 0) {
                        MerchantMessage msg = new MerchantMessage();
                        msg.setMerchantNo(m.getMerchantNo());
                        msg.setMsgType("expire_remind");
                        msg.setTitle("会员到期提醒（" + d + "天）" + today);
                        msg.setContent("您的会员将于 " + d + " 天后到期（"
                                + m.getMemberExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                + "），请及时续费以免业务功能被锁定。");
                        msg.setIsRead(0);
                        msg.setCreateTime(now);
                        messageRepository.save(msg);
                        count++;
                    }
                }
            }
        }
        if (count > 0) {
            log.info("到期提醒生成 count={}", count);
        }
    }
}
