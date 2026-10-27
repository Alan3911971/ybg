package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyChatGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyChatGroupMemberRepository extends JpaRepository<PropertyChatGroupMember, Long> {

    List<PropertyChatGroupMember> findByGroupIdAndStatus(Long groupId, Integer status);

    Optional<PropertyChatGroupMember> findByGroupIdAndOwnerId(Long groupId, Long ownerId);

    long countByGroupIdAndStatus(Long groupId, Integer status);

    long countByCompanyId(Long companyId);
}
