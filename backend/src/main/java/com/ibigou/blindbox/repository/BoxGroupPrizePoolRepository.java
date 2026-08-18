package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.BoxGroupPrizePool;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * BoxGroupPrizePool Repository。
 */
public interface BoxGroupPrizePoolRepository extends JpaRepository<BoxGroupPrizePool, Long> {

    java.util.List<BoxGroupPrizePool> findByMerchantNo(String merchantNo);

    List<BoxGroupPrizePool> findByMerchantNoAndChannelAndEnabled(String merchantNo, Integer channel, Integer enabled);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BoxGroupPrizePool p where p.groupPoolId = :id")
    Optional<BoxGroupPrizePool> findByIdForUpdate(@Param("id") Long id);
}
