package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyVsOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyVsOrderRepository extends JpaRepository<PropertyVsOrder, Long> {

    List<PropertyVsOrder> findByOwnerIdOrderByCreateTimeDesc(Long ownerId);

    List<PropertyVsOrder> findByCompanyIdAndStatusInOrderByCreateTimeDesc(Long companyId, List<Integer> statuses);

    Optional<PropertyVsOrder> findByOrderNo(String orderNo);

    List<PropertyVsOrder> findByMerchantIdAndStatus(Long merchantId, Integer status);
}
