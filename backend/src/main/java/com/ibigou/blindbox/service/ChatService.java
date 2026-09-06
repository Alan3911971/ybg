package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.ChatMessage;
import com.ibigou.blindbox.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 在线客服聊天：顾客与商家双向消息。
 * 顾客发送的消息默认入库 merchantNo 为当前扫码商家；
 * 商家端通过 /api/merchant/chat/* 回复（MerchantController）。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    /** 顾客拉取会话（增量 / 全量） */
    public List<ChatMessage> listForCustomer(String userPhone, String merchantNo, Long afterId) {
        if (userPhone == null || userPhone.isBlank()) {
            throw new BizException("请先登录");
        }
        List<ChatMessage> all = chatMessageRepository.findByUserPhoneAndMerchantNoOrderByIdAsc(userPhone, merchantNo);
        if (afterId == null || afterId <= 0) {
            return all;
        }
        List<ChatMessage> filtered = new ArrayList<>();
        for (ChatMessage m : all) {
            if (m.getId() != null && m.getId() > afterId) {
                filtered.add(m);
            }
        }
        return filtered;
    }

    /** 顾客发送消息 */
    public ChatMessage sendFromCustomer(String userPhone, String merchantNo, String content) {
        if (userPhone == null || userPhone.isBlank()) {
            throw new BizException("请先登录");
        }
        if (content == null || content.isBlank()) {
            throw new BizException("消息不能为空");
        }
        if (content.length() > 500) {
            throw new BizException("消息过长");
        }
        ChatMessage m = new ChatMessage();
        m.setUserPhone(userPhone);
        m.setMerchantNo(merchantNo != null && !merchantNo.isBlank() ? merchantNo : "M1001");
        m.setFromRole("customer");
        m.setContent(content.trim());
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        return chatMessageRepository.save(m);
    }

    /** 商家回复（供 MerchantController 调用） */
    public ChatMessage sendFromMerchant(String userPhone, String merchantNo, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException("消息不能为空");
        }
        ChatMessage m = new ChatMessage();
        m.setUserPhone(userPhone);
        m.setMerchantNo(merchantNo);
        m.setFromRole("merchant");
        m.setContent(content.trim());
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        return chatMessageRepository.save(m);
    }

    /** 商家端：拉取与某顾客的会话 */
    public List<ChatMessage> listSessions(String merchantNo) {
        return chatMessageRepository.findSessionsByMerchantNo(merchantNo);
    }

    public List<ChatMessage> listForMerchant(String userPhone, String merchantNo) {
        return chatMessageRepository.findByUserPhoneAndMerchantNoOrderByIdAsc(userPhone, merchantNo);
    }

    /** 商家端未读计数 */
    public long unreadForMerchant(String merchantNo) {
        return chatMessageRepository.countByMerchantNoAndFromRoleAndIsRead(merchantNo, "customer", 0);
    }

    /** 标记为已读 */
    public void markRead(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<ChatMessage> msgs = chatMessageRepository.findAllById(ids);
        for (ChatMessage m : msgs) {
            m.setIsRead(1);
        }
        chatMessageRepository.saveAll(msgs);
    }
}
