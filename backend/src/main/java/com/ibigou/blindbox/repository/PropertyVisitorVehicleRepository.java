package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyVisitorVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyVisitorVehicleRepository extends JpaRepository<PropertyVisitorVehicle, Long> {

    List<PropertyVisitorVehicle> findByOwnerIdOrderByCreateTimeDesc(Long ownerId);

    Optional<PropertyVisitorVehicle> findByPlateNoAndStatusAndExpireTimeAfter(String plateNo, Integer status, LocalDateTime now);

    List<PropertyVisitorVehicle> findByCommunityIdAndStatusAndExpireTimeAfter(Long communityId, Integer status, LocalDateTime now);
}
