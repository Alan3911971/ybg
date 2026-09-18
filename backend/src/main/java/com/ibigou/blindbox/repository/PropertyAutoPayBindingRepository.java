package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyAutoPayBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyAutoPayBindingRepository extends JpaRepository<PropertyAutoPayBinding, Long> {

    List<PropertyAutoPayBinding> findByOwnerIdAndStatus(Long ownerId, Integer status);

    Optional<PropertyAutoPayBinding> findByPlateNoAndStatus(String plateNo, Integer status);

    Optional<PropertyAutoPayBinding> findByOwnerIdAndPlateNo(Long ownerId, String plateNo);
}
