package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * UserAccount Repository。
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    /** 行锁读取用户账户（并发结算/退款/扣回时防止 total_balance 丢失更新） */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from UserAccount a where a.userPhone = :phone")
    java.util.Optional<UserAccount> findByIdForUpdate(@org.springframework.data.repository.query.Param("phone") String phone);
}
