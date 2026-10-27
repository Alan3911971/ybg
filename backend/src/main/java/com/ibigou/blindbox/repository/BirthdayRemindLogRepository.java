package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.BirthdayRemindLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * BirthdayRemindLog Repository（生日提醒幂等）。
 */
public interface BirthdayRemindLogRepository extends JpaRepository<BirthdayRemindLog, Long> {

    Optional<BirthdayRemindLog> findByMerchantNoAndUserPhoneAndRemindYearAndDaysAhead(
            String merchantNo, String userPhone, Integer remindYear, Integer daysAhead);

    boolean existsByMerchantNoAndUserPhoneAndRemindYearAndDaysAhead(
            String merchantNo, String userPhone, Integer remindYear, Integer daysAhead);
}
