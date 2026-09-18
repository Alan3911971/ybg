package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyDeviceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PropertyDeviceAlertRepository extends JpaRepository<PropertyDeviceAlert, Long> {

    List<PropertyDeviceAlert> findByCompanyIdAndAckStatusOrderByCreateTimeDesc(Long companyId, Integer ackStatus);

    List<PropertyDeviceAlert> findByDeviceIdOrderByCreateTimeDesc(Long deviceId);

    long countByCompanyIdAndAckStatus(Long companyId, Integer ackStatus);

    List<PropertyDeviceAlert> findByCompanyIdAndCreateTimeAfter(Long companyId, LocalDateTime since);
}
