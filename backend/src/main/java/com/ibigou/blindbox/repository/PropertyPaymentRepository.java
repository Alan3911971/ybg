package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyPaymentRepository extends JpaRepository<PropertyPayment, String> {

    Optional<PropertyPayment> findByPaymentNo(String paymentNo);

    List<PropertyPayment> findByBillIdAndStatus(Long billId, Integer status);
}
