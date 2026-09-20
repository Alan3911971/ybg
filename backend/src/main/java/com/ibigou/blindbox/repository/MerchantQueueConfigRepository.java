package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MerchantQueueConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantQueueConfigRepository extends JpaRepository<MerchantQueueConfig, String> {
}
