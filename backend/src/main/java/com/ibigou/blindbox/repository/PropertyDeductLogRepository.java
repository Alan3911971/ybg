package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyDeductLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyDeductLogRepository extends JpaRepository<PropertyDeductLog, Long> {

    List<PropertyDeductLog> findByOwnerIdOrderByCreateTimeDesc(Long ownerId);

    List<PropertyDeductLog> findByBillId(Long billId);
}
