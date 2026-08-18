package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.BoxPublicPool;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * BoxPublicPool Repository。
 */
public interface BoxPublicPoolRepository extends JpaRepository<BoxPublicPool, Long> {

    /** 公共池启用档位，排除投放商家自己 */
    List<BoxPublicPool> findByEnabledAndSourceMerchantNoNot(Integer enabled, String sourceMerchantNo);

    List<BoxPublicPool> findBySourceMerchantNo(String sourceMerchantNo);

    List<BoxPublicPool> findBySourceMerchantNoOrderByPublicIdDesc(String sourceMerchantNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BoxPublicPool p where p.publicId = :publicId")
    Optional<BoxPublicPool> findByIdForUpdate(@Param("publicId") Long publicId);
}
