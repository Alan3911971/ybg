package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyAdminSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PropertyAdminSessionRepository extends JpaRepository<PropertyAdminSession, String> {

    Optional<PropertyAdminSession> findByTokenAndExpireTimeAfter(String token, LocalDateTime now);

    void deleteByAdminId(Long adminId);
}
