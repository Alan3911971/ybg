package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.OfflineOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * OfflineOrder Repository。
 */
public interface OfflineOrderRepository extends JpaRepository<OfflineOrder, String> {

    java.util.Optional<OfflineOrder> findByVoucherToken(String voucherToken);

    java.util.List<OfflineOrder> findByMerchantNoOrderByCreateTimeDesc(String merchantNo);
}
