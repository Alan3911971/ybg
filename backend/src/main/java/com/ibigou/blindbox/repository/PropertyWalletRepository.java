package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PropertyWalletRepository extends JpaRepository<PropertyWallet, Long> {

    Optional<PropertyWallet> findByOwnerIdAndCompanyId(Long ownerId, Long companyId);
}
