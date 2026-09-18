package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyAdminRepository extends JpaRepository<PropertyAdmin, Long> {

    Optional<PropertyAdmin> findByAccount(String account);

    List<PropertyAdmin> findByCompanyId(Long companyId);
}
