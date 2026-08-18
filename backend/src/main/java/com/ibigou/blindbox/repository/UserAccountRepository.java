package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * UserAccount Repository。
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
}
