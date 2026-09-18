package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyOwnerRepository extends JpaRepository<PropertyOwner, Long> {

    Optional<PropertyOwner> findByOwnerPhone(String ownerPhone);

    Optional<PropertyOwner> findByOwnerNo(String ownerNo);

    List<PropertyOwner> findByStatus(Integer status);

    List<PropertyOwner> findByStatusAndBalanceGreaterThan(Integer status, BigDecimal balance);
}
