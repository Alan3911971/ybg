package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.UserBalanceFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UserBalanceFlow Repository（流水禁止物理删除）。
 */
public interface UserBalanceFlowRepository extends JpaRepository<UserBalanceFlow, Long> {

    List<UserBalanceFlow> findByDrawBatchNoAndCanUseAfterDraw(String drawBatchNo, Integer canUseAfterDraw);

    Optional<UserBalanceFlow> findFirstByDrawBatchNoOrderByFlowIdAsc(String drawBatchNo);

    /** 原子激活批次内暂不可用流水（返回受影响行数，并发第二次=0 防余额双加） */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update UserBalanceFlow f set f.canUseAfterDraw = 1 " +
            "where f.drawBatchNo = :batch and f.canUseAfterDraw = 0")
    int activateByBatch(@org.springframework.data.repository.query.Param("batch") String batch);

    /** 批次发放流水金额合计（amount>0 的发放流水） */
    @org.springframework.data.jpa.repository.Query("select sum(f.amount) from UserBalanceFlow f " +
            "where f.drawBatchNo = :batch and f.amount > 0")
    java.math.BigDecimal sumGrantByBatch(@org.springframework.data.repository.query.Param("batch") String batch);

    List<UserBalanceFlow> findByUserPhoneOrderByFlowIdDesc(String userPhone);

    List<UserBalanceFlow> findByMerchantNoAndFlowTypeAndCreateTimeBetween(
            String merchantNo, Integer flowType, java.time.LocalDateTime from, java.time.LocalDateTime to);

    List<UserBalanceFlow> findByMerchantNoAndFlowTypeInAndCreateTimeBetween(
            String merchantNo, java.util.Collection<Integer> flowTypes,
            java.time.LocalDateTime from, java.time.LocalDateTime to);

    @Modifying
    @Query("update UserBalanceFlow f set f.canUseAfterDraw = 1 where f.drawBatchNo = :batchNo and f.canUseAfterDraw = 0")
    int activateDrawBatch(@Param("batchNo") String batchNo);

    /** 补偿任务：扫描仍暂不可用的发放流水批次 */
    @Query("select distinct f.drawBatchNo from UserBalanceFlow f " +
           "where f.canUseAfterDraw = 0 and f.drawBatchNo is not null and f.createTime < :before")
    List<String> findPendingBatches(@Param("before") LocalDateTime before);
}
