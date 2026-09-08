package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * MemberProfile Repository（会员资料）。
 */
public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {

    Optional<MemberProfile> findByMerchantNoAndUserPhone(String merchantNo, String userPhone);
}
