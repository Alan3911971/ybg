package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.OfflineOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * OfflineOrder Repository。
 */
public interface OfflineOrderRepository extends JpaRepository<OfflineOrder, String> {

    /** 行锁读取订单（并发退款/确认防重复处理） */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from OfflineOrder o where o.offlineOrderNo = :no")
    java.util.Optional<OfflineOrder> findByIdForUpdate(@org.springframework.data.repository.query.Param("no") String no);

    java.util.Optional<OfflineOrder> findByVoucherToken(String voucherToken);

    java.util.List<OfflineOrder> findByMerchantNoOrderByCreateTimeDesc(String merchantNo);
}
