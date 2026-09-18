package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRoomRepository extends JpaRepository<PropertyRoom, Long> {

    Optional<PropertyRoom> findByBuildingIdAndUnitNoAndFloorNoAndRoomNo(Long buildingId, Integer unitNo, Integer floorNo, String roomNo);

    List<PropertyRoom> findByBuildingId(Long buildingId);
}
