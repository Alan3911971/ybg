package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertySplitRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertySplitRecordRepository extends JpaRepository<PropertySplitRecord, Long> {

    Optional<PropertySplitRecord> findByOrderNo(String orderNo);

    List<PropertySplitRecord> findByOwnerIdOrderByCreateTimeDesc(Long ownerId);

    List<PropertySplitRecord> findBySplitStatus(Integer splitStatus);

    List<PropertySplitRecord> findByCompanyId(Long companyId);
}
