package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MemberVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * MemberVisit Repository（回访记录）。
 */
public interface MemberVisitRepository extends JpaRepository<MemberVisit, Long> {

    List<MemberVisit> findByMerchantNoAndUserPhoneOrderByCreateTimeDesc(String merchantNo, String userPhone);
}
