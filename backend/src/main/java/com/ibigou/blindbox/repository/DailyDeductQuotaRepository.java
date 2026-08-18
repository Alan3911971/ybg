package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.DailyDeductQuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * DailyDeductQuota Repository。
 */
public interface DailyDeductQuotaRepository extends JpaRepository<DailyDeductQuota, Long> {

    Optional<DailyDeductQuota> findByUserPhoneAndMerchantNoAndDeductDate(
            String userPhone, String merchantNo, LocalDate deductDate);

    /** 行锁读取（并发防超单日上限，与中奖限额一致） */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select q from DailyDeductQuota q " +
            "where q.userPhone = :userPhone and q.merchantNo = :merchantNo and q.deductDate = :date")
    Optional<DailyDeductQuota> findForUpdate(
            @org.springframework.data.repository.query.Param("userPhone") String userPhone,
            @org.springframework.data.repository.query.Param("merchantNo") String merchantNo,
            @org.springframework.data.repository.query.Param("date") LocalDate date);
}
