package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyReservationRepository extends JpaRepository<PropertyReservation, Long> {

    List<PropertyReservation> findByFacilityIdAndReserveDateAndStatusNotIn(Long facilityId, LocalDate date, List<Integer> excludedStatuses);

    List<PropertyReservation> findByOwnerIdOrderByCreateTimeDesc(Long ownerId);

    List<PropertyReservation> findByCompanyIdAndStatusInOrderByCreateTimeDesc(Long companyId, List<Integer> statuses);

    Optional<PropertyReservation> findByReservationNo(String reservationNo);
}
