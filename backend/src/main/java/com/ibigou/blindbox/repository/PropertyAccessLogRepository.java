package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PropertyAccessLogRepository extends JpaRepository<PropertyAccessLog, Long> {

    List<PropertyAccessLog> findByCommunityIdAndPassTimeBetweenOrderByPassTimeDesc(Long communityId, LocalDateTime start, LocalDateTime end);

    List<PropertyAccessLog> findByCredentialId(Long credentialId);
}
