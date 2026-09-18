package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PropertyCompanyRepository extends JpaRepository<PropertyCompany, Long> {

    Optional<PropertyCompany> findByWxMchId(String wxMchId);
}
