package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * UserCoupon Repository。
 */
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    java.util.List<UserCoupon> findByDrawBatchNo(String drawBatchNo);

    List<UserCoupon> findAllByUserPhoneAndStatusAndCanUseAfterDraw(String userPhone, Integer status, Integer canUseAfterDraw);

    List<UserCoupon> findByUserPhoneOrderByCouponIdDesc(String userPhone);

    /** 外来公共券：指定用户集合中，发行商家不是本店的未使用券 */
    @org.springframework.data.jpa.repository.Query("select c from UserCoupon c " +
            "where c.userPhone in :phones and c.sourceMerchantNo <> :merchantNo and c.status = 0")
    java.util.List<UserCoupon> findExternalCoupons(
            @org.springframework.data.repository.query.Param("phones") java.util.Collection<String> phones,
            @org.springframework.data.repository.query.Param("merchantNo") String merchantNo);

    /** 核销 CAS：仅 status=0(未使用) 可置已核销，返回受影响行数（0=已被并发核销） */
    @Modifying
    @Query("update UserCoupon c set c.status = 1, c.verifyType = :vt, c.bizNo = :bizNo, c.updateTime = :now " +
           "where c.couponId = :id and c.status = 0")
    int verifyCas(@Param("id") Long id, @Param("vt") Integer vt, @Param("bizNo") String bizNo,
                  @Param("now") LocalDateTime now);

    /** 流程闭环：批次内暂不可用券全部置可用 */
    @Modifying
    @Query("update UserCoupon c set c.canUseAfterDraw = 1, c.updateTime = :now " +
           "where c.drawBatchNo = :batchNo and c.canUseAfterDraw = 0")
    int activateDrawBatch(@Param("batchNo") String batchNo, @Param("now") LocalDateTime now);

    /** 定时任务：把已过期未使用的券标记 status=2 */
    @Modifying
    @Query("update UserCoupon c set c.status = 2, c.updateTime = :now " +
           "where c.status = 0 and c.validEnd < :now")
    int markExpired(@Param("now") LocalDateTime now);

    /** 补偿任务：扫描仍暂不可用且抽出超过指定时长的批次号 */
    @Query("select distinct c.drawBatchNo from UserCoupon c " +
           "where c.canUseAfterDraw = 0 and c.drawBatchNo is not null and c.createTime < :before")
    List<String> findPendingBatches(@Param("before") LocalDateTime before);

    /** 全店最近中奖记录 */
    List<UserCoupon> findBySourceMerchantNoOrderByCreateTimeDesc(String merchantNo);
}
