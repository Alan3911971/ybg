package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.IbigouGoods;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * IbigouGoods Repository。
 */
public interface IbigouGoodsRepository extends JpaRepository<IbigouGoods, Long> {

    List<IbigouGoods> findByEnabledOrderBySortOrderDescSalesCountDesc(Integer enabled);

    List<IbigouGoods> findByMerchantNoOrderBySortOrderDesc(String merchantNo);

    List<IbigouGoods> findByMerchantNoAndEnabledOrderBySortOrderDesc(String merchantNo, Integer enabled);
}
