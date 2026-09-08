package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MemberGift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberGiftRepository extends JpaRepository<MemberGift, Long> {
    List<MemberGift> findByMerchantNoAndUserPhoneOrderByCreateTimeDesc(String merchantNo, String userPhone);
}
