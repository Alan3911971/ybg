package com.ibigou.blindbox.service;

import com.ibigou.blindbox.common.BizException;
import com.ibigou.blindbox.entity.UserMessage;
import com.ibigou.blindbox.repository.UserMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * P1 用户站内消息（跨店返还/退款等触达）。
 */
@Service
@RequiredArgsConstructor
public class UserMessageService {

    private final UserMessageRepository messageRepository;

    @Transactional
    public UserMessage record(String userPhone, String msgType, String title, String content) {
        UserMessage msg = new UserMessage();
        msg.setUserPhone(userPhone);
        msg.setMsgType(msgType);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        return messageRepository.save(msg);
    }

    public List<UserMessage> list(String userPhone) {
        return messageRepository.findByUserPhoneOrderByIdDesc(userPhone);
    }

    public long unreadCount(String userPhone) {
        return messageRepository.countByUserPhoneAndIsRead(userPhone, 0);
    }

    @Transactional
    public void markRead(Long id, String userPhone) {
        UserMessage msg = messageRepository.findById(id)
                .filter(m -> m.getUserPhone().equals(userPhone))
                .orElseThrow(() -> new BizException("消息不存在"));
        msg.setIsRead(1);
        messageRepository.save(msg);
    }

    @Transactional
    public void markAllRead(String userPhone) {
        for (UserMessage m : messageRepository.findByUserPhoneOrderByIdDesc(userPhone)) {
            if (m.getIsRead() == 0) {
                m.setIsRead(1);
                messageRepository.save(m);
            }
        }
    }
}
