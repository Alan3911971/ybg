package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyParkingFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyParkingFeeRepository extends JpaRepository<PropertyParkingFee, Long> {

    List<PropertyParkingFee> findByOwnerIdAndCompanyIdAndStatusInOrderByDueDateAsc(Long ownerId, Long companyId, List<Integer> statuses);

    List<PropertyParkingFee> findByVehicleIdAndPeriod(Long vehicleId, String period);

    Optional<PropertyParkingFee> findByFeeNo(String feeNo);
}
