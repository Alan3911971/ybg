package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyBill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyBillRepository extends JpaRepository<PropertyBill, Long> {

    List<PropertyBill> findByOwnerIdAndCompanyIdAndStatusInOrderByDueDateAsc(Long ownerId, Long companyId, List<Integer> statuses);

    List<PropertyBill> findByCompanyIdAndBillPeriod(Long companyId, String billPeriod);

    Page<PropertyBill> findByCompanyIdAndBillPeriod(Long companyId, String billPeriod, Pageable pageable);

    Page<PropertyBill> findByCompanyId(Long companyId, Pageable pageable);

    Optional<PropertyBill> findByBillNo(String billNo);

    Optional<PropertyBill> findByRoomIdAndBillPeriod(Long roomId, String billPeriod);

    List<PropertyBill> findByRoomIdOrderByBillPeriodAsc(Long roomId);
}
