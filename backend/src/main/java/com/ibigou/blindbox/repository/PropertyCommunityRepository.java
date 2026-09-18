package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyCommunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyCommunityRepository extends JpaRepository<PropertyCommunity, Long> {

    List<PropertyCommunity> findByCompanyId(Long companyId);
}
