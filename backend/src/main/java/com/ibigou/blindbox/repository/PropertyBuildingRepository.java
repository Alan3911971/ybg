package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyBuilding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyBuildingRepository extends JpaRepository<PropertyBuilding, Long> {

    List<PropertyBuilding> findByCommunityId(Long communityId);
}
