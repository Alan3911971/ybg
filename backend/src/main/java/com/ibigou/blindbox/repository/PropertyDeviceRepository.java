package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyDeviceRepository extends JpaRepository<PropertyDevice, Long> {

    List<PropertyDevice> findByCompanyIdAndStatus(Long companyId, Integer status);

    List<PropertyDevice> findByCompanyIdOrderByCreateTimeDesc(Long companyId);

    Optional<PropertyDevice> findByDeviceNo(String deviceNo);
}
