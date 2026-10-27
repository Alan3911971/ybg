package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyParkingTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyParkingTransferRepository extends JpaRepository<PropertyParkingTransfer, Long> {

    List<PropertyParkingTransfer> findByCompanyIdOrderByCreateTimeDesc(Long companyId);

    List<PropertyParkingTransfer> findByCompanyIdAndStatusOrderByCreateTimeDesc(Long companyId, Integer status);

    long countByCompanyIdAndStatus(Long companyId, Integer status);
}
