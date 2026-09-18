package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyAlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyAlertRuleRepository extends JpaRepository<PropertyAlertRule, Long> {

    List<PropertyAlertRule> findByCompanyIdAndStatus(Long companyId, Integer status);

    List<PropertyAlertRule> findByDeviceTypeAndAlertTypeAndStatus(String deviceType, String alertType, Integer status);
}
