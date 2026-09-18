package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyAccessCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyAccessCredentialRepository extends JpaRepository<PropertyAccessCredential, Long> {

    Optional<PropertyAccessCredential> findByCredValueAndStatusAndExpireTimeAfter(String credValue, Integer status, LocalDateTime now);

    List<PropertyAccessCredential> findByOwnerIdAndStatus(Long ownerId, Integer status);
}
