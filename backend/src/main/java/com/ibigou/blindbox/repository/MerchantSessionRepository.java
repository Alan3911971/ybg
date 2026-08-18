package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MerchantSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MerchantSession Repository。
 */
public interface MerchantSessionRepository extends JpaRepository<MerchantSession, String> {

    java.util.List<MerchantSession> findByMerchantNo(String merchantNo);

    List<MerchantSession> findByExpireTimeBefore(LocalDateTime before);
}
