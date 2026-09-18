package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyActivityAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PropertyActivityAuditRepository extends JpaRepository<PropertyActivityAudit, Long> {

    List<PropertyActivityAudit> findByMerchantIdAndAuditStatus(Long merchantId, Integer auditStatus);

    List<PropertyActivityAudit> findByAuditStatus(Integer auditStatus);

    List<PropertyActivityAudit> findByMerchantIdAndStartTimeBeforeAndEndTimeAfter(Long merchantId, LocalDateTime now1, LocalDateTime now2);
}
