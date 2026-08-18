package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.CustomerSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CustomerSession Repository。
 */
public interface CustomerSessionRepository extends JpaRepository<CustomerSession, String> {

    List<CustomerSession> findByExpireTimeBefore(LocalDateTime before);
}
