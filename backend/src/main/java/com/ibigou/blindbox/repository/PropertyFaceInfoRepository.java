package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyFaceInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyFaceInfoRepository extends JpaRepository<PropertyFaceInfo, Long> {

    List<PropertyFaceInfo> findByOwnerIdAndStatus(Long ownerId, Integer status);

    List<PropertyFaceInfo> findByAuditStatus(Integer auditStatus);

    Optional<PropertyFaceInfo> findByOwnerIdAndMemberIdAndRoomId(Long ownerId, Long memberId, Long roomId);
}
