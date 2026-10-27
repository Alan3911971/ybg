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

    /** 违停识别用：同车牌可能多业态（业主/月租/临时），全取再筛 */
    List<PropertyVehicle> findByPlateNoOrderByStatusDesc(String plateNo);
}
