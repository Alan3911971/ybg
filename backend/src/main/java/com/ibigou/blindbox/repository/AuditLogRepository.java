package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AuditLog Repository。
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    void deleteByCreateTimeBefore(java.time.LocalDateTime before);

    java.util.List<AuditLog> findAllByOrderByLogIdDesc();
}
