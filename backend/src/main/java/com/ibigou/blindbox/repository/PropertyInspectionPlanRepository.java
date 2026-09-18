package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyInspectionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyInspectionPlanRepository extends JpaRepository<PropertyInspectionPlan, Long> {

    List<PropertyInspectionPlan> findByCompanyIdAndStatus(Long companyId, Integer status);
}
