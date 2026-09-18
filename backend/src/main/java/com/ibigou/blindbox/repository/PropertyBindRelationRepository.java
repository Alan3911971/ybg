package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyBindRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyBindRelationRepository extends JpaRepository<PropertyBindRelation, Long> {

    List<PropertyBindRelation> findByOwnerIdAndStatusOrderByBindTimeAsc(Long ownerId, Integer status);

    Optional<PropertyBindRelation> findFirstByOwnerIdAndStatusOrderByBindTimeAsc(Long ownerId, Integer status);

    Optional<PropertyBindRelation> findByOwnerIdAndCompanyId(Long ownerId, Long companyId);

    List<PropertyBindRelation> findByCompanyIdAndStatus(Long companyId, Integer status);

    Optional<PropertyBindRelation> findByRoomIdAndStatus(Long roomId, Integer status);

    /** 鍙屾ā寮忔牳蹇冩煡璇細鎸変笟涓?鐗╀笟鍏徃+鐘舵€佹煡鎵炬渶鏃╃粦瀹?*/
    Optional<PropertyBindRelation> findFirstByOwnerIdAndCompanyIdAndStatusOrderByBindTimeAsc(
            Long ownerId, Long companyId, Integer status);
}
