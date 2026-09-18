package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyFacilityRepository extends JpaRepository<PropertyFacility, Long> {

    List<PropertyFacility> findByCompanyIdAndStatusOrderByCreateTimeDesc(Long companyId, Integer status);

    List<PropertyFacility> findByCompanyIdOrderByCreateTimeDesc(Long companyId);
}
