package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MemberOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * MemberOrder Repository。
 */
public interface MemberOrderRepository extends JpaRepository<MemberOrder, String> {

    List<MemberOrder> findByMerchantNoOrderByCreateTimeDesc(String merchantNo);

    /** P2：待支付超时订单（补单任务） */
    List<MemberOrder> findByStatusAndCreateTimeBefore(Integer status, java.time.LocalDateTime before);
}
