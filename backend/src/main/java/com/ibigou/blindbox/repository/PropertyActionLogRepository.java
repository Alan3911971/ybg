package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PropertyActionLogRepository extends JpaRepository<PropertyActionLog, Long> {

    List<PropertyActionLog> findByCompanyIdOrderByLogIdDesc(Long companyId);

    List<PropertyActionLog> findByCompanyIdAndCreateTimeAfterOrderByLogIdDesc(Long companyId, LocalDateTime after);

    List<PropertyActionLog> findByCompanyIdAndTargetTypeAndTargetIdOrderByLogIdDesc(Long companyId, String targetType, Long targetId);

    List<PropertyActionLog> findByCompanyIdAndActionOrderByLogIdDesc(Long companyId, String action);

    long countByCompanyIdAndCreateTimeAfter(Long companyId, LocalDateTime after);

    long countByCompanyIdAndStatusAndCreateTimeAfter(Long companyId, Integer status, LocalDateTime after);
}
