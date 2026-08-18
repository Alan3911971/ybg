package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.UserMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * UserMessage Repository。
 */
public interface UserMessageRepository extends JpaRepository<UserMessage, Long> {

    void deleteByCreateTimeBefore(java.time.LocalDateTime before);

    List<UserMessage> findByUserPhoneOrderByIdDesc(String userPhone);

    long countByUserPhoneAndIsRead(String userPhone, Integer isRead);
}
