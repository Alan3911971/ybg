package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyValueService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyValueServiceRepository extends JpaRepository<PropertyValueService, Long> {

    List<PropertyValueService> findByCompanyIdAndStatusOrderBySortOrderAsc(Long companyId, Integer status);

    List<PropertyValueService> findByMerchantIdAndStatus(Long merchantId, Integer status);
}
