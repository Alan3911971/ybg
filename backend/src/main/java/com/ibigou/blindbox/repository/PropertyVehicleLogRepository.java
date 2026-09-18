package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyVehicleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PropertyVehicleLogRepository extends JpaRepository<PropertyVehicleLog, Long> {

    List<PropertyVehicleLog> findByPlateNoAndCreateTimeBetweenOrderByCreateTimeDesc(String plateNo, LocalDateTime start, LocalDateTime end);

    List<PropertyVehicleLog> findByCommunityIdAndCreateTimeBetweenOrderByCreateTimeDesc(Long communityId, LocalDateTime start, LocalDateTime end);
}
