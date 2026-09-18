package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyVehicleRepository extends JpaRepository<PropertyVehicle, Long> {

    Optional<PropertyVehicle> findByPlateNo(String plateNo);

    List<PropertyVehicle> findByOwnerIdAndStatus(Long ownerId, Integer status);

    List<PropertyVehicle> findByRoomIdAndStatus(Long roomId, Integer status);

    List<PropertyVehicle> findByOwnerIdIn(List<Long> ownerIds);
}
