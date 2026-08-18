package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MerchantMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * MerchantMessage Repository。
 */
public interface MerchantMessageRepository extends JpaRepository<MerchantMessage, Long> {

    void deleteByCreateTimeBefore(java.time.LocalDateTime before);

    List<MerchantMessage> findByMerchantNoOrderByIdDesc(String merchantNo);

    long countByMerchantNoAndIsRead(String merchantNo, Integer isRead);

    /** 幂等：检查某商家某类型某日期是否已生成提醒 */
    @Query("select count(m) from MerchantMessage m where m.merchantNo = :merchantNo " +
           "and m.msgType = :type and m.title like %:date%")
    long countExist(@Param("merchantNo") String merchantNo, @Param("type") String type,
                    @Param("date") String date);
}
