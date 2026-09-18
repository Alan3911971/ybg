package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyInspectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PropertyInspectionRecordRepository extends JpaRepository<PropertyInspectionRecord, Long> {

    List<PropertyInspectionRecord> findByCompanyIdAndCheckDateBetweenOrderByCheckTimeDesc(Long companyId, LocalDate start, LocalDate end);

    List<PropertyInspectionRecord> findByInspectorIdAndCheckDate(Long inspectorId, LocalDate checkDate);
}
