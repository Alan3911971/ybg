package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyWorkorder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyWorkorderRepository extends JpaRepository<PropertyWorkorder, Long> {

    List<PropertyWorkorder> findByOwnerIdOrderByCreateTimeDesc(Long ownerId);

    List<PropertyWorkorder> findByCompanyIdAndStatusInOrderByCreateTimeDesc(Long companyId, List<Integer> statuses);

    List<PropertyWorkorder> findByAssigneeIdAndStatusIn(Long assigneeId, List<Integer> statuses);

    Optional<PropertyWorkorder> findByWoNo(String woNo);
}
