package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * AdminUser Repository。
 */
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByAccount(String account);
}
