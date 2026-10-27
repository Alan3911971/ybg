package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyBillRelief;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyBillReliefRepository extends JpaRepository<PropertyBillRelief, Long> {

    List<PropertyBillRelief> findByCompanyIdOrderByReliefIdDesc(Long companyId);

    List<PropertyBillRelief> findByCompanyIdAndStatusOrderByReliefIdDesc(Long companyId, Integer status);

    List<PropertyBillRelief> findByCompanyIdAndBillId(Long companyId, Long billId);

    List<PropertyBillRelief> findByCompanyIdAndOwnerIdOrderByReliefIdDesc(Long companyId, Long ownerId);
}
