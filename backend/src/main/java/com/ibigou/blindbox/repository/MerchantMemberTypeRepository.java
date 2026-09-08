package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MerchantMemberType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantMemberTypeRepository extends JpaRepository<MerchantMemberType, Long> {
    List<MerchantMemberType> findByMerchantNoOrderBySortOrderAsc(String merchantNo);
    Optional<MerchantMemberType> findByMerchantNoAndTypeName(String merchantNo, String typeName);
}
