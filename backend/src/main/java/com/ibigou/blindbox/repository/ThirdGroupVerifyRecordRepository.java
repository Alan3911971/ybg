package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.ThirdGroupVerifyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ThirdGroupVerifyRecord Repository。
 */
public interface ThirdGroupVerifyRecordRepository extends JpaRepository<ThirdGroupVerifyRecord, Long> {

    java.util.List<ThirdGroupVerifyRecord> findByChannelAndCreateTimeBetween(
            Integer channel, java.time.LocalDateTime from, java.time.LocalDateTime to);

    java.util.List<ThirdGroupVerifyRecord> findByCreateTimeBetween(
            java.time.LocalDateTime from, java.time.LocalDateTime to);
}
