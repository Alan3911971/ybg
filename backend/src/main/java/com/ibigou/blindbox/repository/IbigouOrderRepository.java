package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.IbigouOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * IbigouOrder Repository。
 */
public interface IbigouOrderRepository extends JpaRepository<IbigouOrder, String> {

    java.util.List<IbigouOrder> findByMerchantNoAndCreateTimeBetween(
            String merchantNo, java.time.LocalDateTime from, java.time.LocalDateTime to);

    java.util.List<IbigouOrder> findByUserPhoneOrderByCreateTimeDesc(String userPhone);

    java.util.List<IbigouOrder> findByMerchantNoOrderByCreateTimeDesc(String merchantNo);

    java.util.List<IbigouOrder> findByCreateTimeBetween(
            java.time.LocalDateTime from, java.time.LocalDateTime to);
}
