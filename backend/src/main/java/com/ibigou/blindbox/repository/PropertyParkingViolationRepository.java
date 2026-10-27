package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyParkingViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PropertyParkingViolationRepository extends JpaRepository<PropertyParkingViolation, Long> {

    List<PropertyParkingViolation> findByCompanyIdOrderByCreateTimeDesc(Long companyId);

    List<PropertyParkingViolation> findByCompanyIdAndStatusOrderByCreateTimeDesc(Long companyId, Integer status);

    List<PropertyParkingViolation> findBySubmitOwnerIdOrderByCreateTimeDesc(Long ownerId);

    /** 同车牌 24h 内已有记录（防邻居互拍累积） */
    List<PropertyParkingViolation> findByCarPlateAndCreateTimeAfter(String carPlate, LocalDateTime after);

    long countByCompanyIdAndCreateTimeAfter(Long companyId, LocalDateTime after);
}
