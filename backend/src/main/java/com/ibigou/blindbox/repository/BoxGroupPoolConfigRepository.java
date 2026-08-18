package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.BoxGroupPoolConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * BoxGroupPoolConfig Repository。
 */
public interface BoxGroupPoolConfigRepository extends JpaRepository<BoxGroupPoolConfig, Long> {

    Optional<BoxGroupPoolConfig> findByMerchantNoAndChannel(String merchantNo, Integer channel);
}
