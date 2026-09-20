package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.WxUserOpenid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WxUserOpenidRepository extends JpaRepository<WxUserOpenid, String> {
    Optional<WxUserOpenid> findByPhone(String phone);
}
