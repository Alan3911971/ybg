package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyMerchantReferral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyMerchantReferralRepository extends JpaRepository<PropertyMerchantReferral, Long> {

    Optional<PropertyMerchantReferral> findByCode(String code);

    List<PropertyMerchantReferral> findByCompanyIdOrderByCreateTimeDesc(Long companyId);

    List<PropertyMerchantReferral> findByCompanyIdAndStatusOrderByCreateTimeDesc(Long companyId, Integer status);

    Optional<PropertyMerchantReferral> findByMerchantNo(String merchantNo);

    /** 一人一码：24h 内未过期的活跃码只能有一个 */
    List<PropertyMerchantReferral> findByOwnerIdAndStatusInAndExpireTimeAfter(Long ownerId, List<Integer> statuses, LocalDateTime after);

    long countByCompanyIdAndReward30Status(Long companyId, Integer reward30Status);

    long countByCompanyIdAndCreateTimeAfter(Long companyId, LocalDateTime after);
}
