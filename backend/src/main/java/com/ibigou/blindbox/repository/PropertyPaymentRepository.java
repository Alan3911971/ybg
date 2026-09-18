package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyPaymentRepository extends JpaRepository<PropertyPayment, String> {

    Optional<PropertyPayment> findByPaymentNo(String paymentNo);

    List<PropertyPayment> findByBillIdAndStatus(Long billId, Integer status);

    List<PropertyPayment> findByBillIdAndFeeTypeAndStatus(Long billId, Integer feeType, Integer status);

    Page<PropertyPayment> findByCompanyId(Long companyId, Pageable pageable);

    Page<PropertyPayment> findByCompanyIdAndStatus(Long companyId, Integer status, Pageable pageable);

    Page<PropertyPayment> findByStatus(Integer status, Pageable pageable);
}
