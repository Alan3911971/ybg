package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.BoxPrizeLimitStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * BoxPrizeLimitStat Repository：中奖限额统计（退款不回滚计数）。
 */
public interface BoxPrizeLimitStatRepository extends JpaRepository<BoxPrizeLimitStat, Long> {

    /** 一天一次规则：统计指定用户在指定商家当天的参与次数 */
    long countByMerchantNoAndUserPhoneAndCreateTimeAfter(String merchantNo, String userPhone,
                                                         java.time.LocalDateTime since);

    void deleteByMerchantNo(String merchantNo);

    /** 某商家累计参与人次（开奖播报"第X位用户"） */
    @Query("select count(s) from BoxPrizeLimitStat s where s.merchantNo = :merchantNo")
    long countByMerchantNo(@Param("merchantNo") String merchantNo);

    /** 在某商家中奖过的用户手机号（外来券判定用） */
    @Query("select distinct s.userPhone from BoxPrizeLimitStat s where s.merchantNo = :merchantNo")
    java.util.List<String> findDistinctUserPhonesByMerchantNo(
            @Param("merchantNo") String merchantNo);

    /** 统计某奖品在某时间窗内被某商家用户抽中次数 */
    @Query("select count(s) from BoxPrizeLimitStat s where s.prizeId = :prizeId and s.createTime >= :since and s.createTime < :until")
    long countByPrize(@Param("prizeId") Long prizeId,
                      @Param("since") LocalDateTime since,
                      @Param("until") LocalDateTime until);

    /** 统计某奖品在某商家内被抽中次数 */
    @Query("select count(s) from BoxPrizeLimitStat s where s.prizeId = :prizeId and s.merchantNo = :merchantNo and s.createTime >= :since and s.createTime < :until")
    long countByPrizeAndMerchant(@Param("prizeId") Long prizeId,
                                 @Param("merchantNo") String merchantNo,
                                 @Param("since") LocalDateTime since,
                                 @Param("until") LocalDateTime until);

    /** 统计某奖品被某用户抽中次数 */
    @Query("select count(s) from BoxPrizeLimitStat s where s.prizeId = :prizeId and s.userPhone = :userPhone and s.createTime >= :since and s.createTime < :until")
    long countByPrizeAndUser(@Param("prizeId") Long prizeId,
                             @Param("userPhone") String userPhone,
                             @Param("since") LocalDateTime since,
                             @Param("until") LocalDateTime until);
}
