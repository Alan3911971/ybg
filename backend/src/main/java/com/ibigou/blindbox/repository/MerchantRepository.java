package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Merchant Repository。
 */
public interface MerchantRepository extends JpaRepository<Merchant, String> {

    java.util.Optional<Merchant> findByLoginAccount(String loginAccount);
}
