package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MerchantAd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerchantAdRepository extends JpaRepository<MerchantAd, Long> {

    List<MerchantAd> findByMerchantNoOrderBySortAscCreateTimeDesc(String merchantNo);

    List<MerchantAd> findByScopeAndAuditStatusAndEnabledOrderBySortAscCreateTimeDesc(Integer scope, Integer auditStatus, Integer enabled);

    List<MerchantAd> findByAuditStatusOrderByCreateTimeDesc(Integer auditStatus);
}
