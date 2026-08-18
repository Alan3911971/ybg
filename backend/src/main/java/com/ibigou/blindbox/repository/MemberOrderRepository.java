package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MemberOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * MemberOrder Repository。
 */
public interface MemberOrderRepository extends JpaRepository<MemberOrder, String> {

    /** 行锁读取订单（并发续费确认防重复叠加） */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from MemberOrder o where o.orderNo = :no")
    java.util.Optional<MemberOrder> findByIdForUpdate(@org.springframework.data.repository.query.Param("no") String no);

    List<MemberOrder> findByMerchantNoOrderByCreateTimeDesc(String merchantNo);

    /** P2：待支付超时订单（补单任务） */
    List<MemberOrder> findByStatusAndCreateTimeBefore(Integer status, java.time.LocalDateTime before);
}
