package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyChatGroupRepository extends JpaRepository<PropertyChatGroup, Long> {

    List<PropertyChatGroup> findByCompanyIdAndStatusOrderByGroupIdAsc(Long companyId, Integer status);

    long countByCompanyIdAndStatus(Long companyId, Integer status);
}
