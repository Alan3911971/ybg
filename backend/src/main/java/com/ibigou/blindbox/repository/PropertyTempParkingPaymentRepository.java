package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyTempParkingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyTempParkingPaymentRepository extends JpaRepository<PropertyTempParkingPayment, Long> {

    List<PropertyTempParkingPayment> findByCommunityIdAndPayTimeBetweenOrderByPayTimeDesc(Long communityId, LocalDateTime start, LocalDateTime end);

    Optional<PropertyTempParkingPayment> findByVehicleLogId(Long vehicleLogId);

    Optional<PropertyTempParkingPayment> findByPaymentNo(String paymentNo);
}
