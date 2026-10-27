package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyDrawRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PropertyDrawRecordRepository extends JpaRepository<PropertyDrawRecord, Long> {

    List<PropertyDrawRecord> findByPhoneOrderByRecordIdDesc(String phone);

    @Query("select count(r) from PropertyDrawRecord r where r.companyId = :cid")
    long countByCompany(@Param("cid") Long cid);

    @Query("select count(r) from PropertyDrawRecord r where r.companyId = :cid and r.drawDate = :d")
    long countByCompanyAndDate(@Param("cid") Long cid, @Param("d") LocalDate d);

    @Query("select count(r) from PropertyDrawRecord r where r.phone = :p")
    long countByPhone(@Param("p") String phone);

    List<PropertyDrawRecord> findByPhoneAndCouponNoIsNotNullOrderByRecordIdDesc(String phone);
}
