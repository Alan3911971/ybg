package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.BoxPrizePool;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * BoxPrizePool Repository。
 */
public interface BoxPrizePoolRepository extends JpaRepository<BoxPrizePool, Long> {

    List<BoxPrizePool> findByMerchantNoAndEnabled(String merchantNo, Integer enabled);

    List<BoxPrizePool> findByMerchantNoOrderByPrizeIdDesc(String merchantNo);

    List<BoxPrizePool> findByMerchantNoAndPrizeTypeAndPrizeValue(String merchantNo, Integer prizeType, java.math.BigDecimal prizeValue);

    /** 行锁读取档位（并发开奖串行化，防止超出限额） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BoxPrizePool p where p.prizeId = :prizeId")
    Optional<BoxPrizePool> findByIdForUpdate(@Param("prizeId") Long prizeId);
}
