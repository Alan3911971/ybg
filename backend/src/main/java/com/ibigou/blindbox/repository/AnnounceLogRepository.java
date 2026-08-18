package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.AnnounceLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AnnounceLog Repository。
 */
public interface AnnounceLogRepository extends JpaRepository<AnnounceLog, Long> {

    void deleteByCreateTimeBefore(java.time.LocalDateTime before);

    List<AnnounceLog> findByMerchantNoOrderByAnnounceIdAsc(String merchantNo);

    List<AnnounceLog> findByMerchantNoAndAnnounceIdGreaterThanOrderByAnnounceIdAsc(
            String merchantNo, Long afterId);
}
