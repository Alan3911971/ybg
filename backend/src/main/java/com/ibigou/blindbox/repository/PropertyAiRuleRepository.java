package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyAiRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyAiRuleRepository extends JpaRepository<PropertyAiRule, Long> {

    List<PropertyAiRule> findByCompanyIdInOrderBySortNoAsc(List<Long> companyIds);

    List<PropertyAiRule> findByCompanyIdOrderBySortNoAsc(Long companyId);

    Optional<PropertyAiRule> findByCompanyIdAndEventCode(Long companyId, String eventCode);

    long countByCompanyIdAndAuthorizeLevel(Long companyId, Integer authorizeLevel);
}
