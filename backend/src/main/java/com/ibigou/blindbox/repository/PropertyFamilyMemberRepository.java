package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyFamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyFamilyMemberRepository extends JpaRepository<PropertyFamilyMember, Long> {

    List<PropertyFamilyMember> findByOwnerIdAndStatus(Long ownerId, Integer status);

    Optional<PropertyFamilyMember> findByMemberPhone(String memberPhone);

    Optional<PropertyFamilyMember> findByMemberPhoneAndStatus(String memberPhone, Integer status);
}
