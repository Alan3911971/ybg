package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MerchantPropertyBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商家-物业关联表 Repository：定义分账通道配置。
 * 支持双模式：
 *   splitMode=1 商家→物业：消费者在商家消费，商家分账到关联物业公司
 *   splitMode=2 平台→物业：消费者在宜必购平台消费，平台分账到关联物业公司
 * 一个商家可同时启用两种模式（分别创建两条记录）。
 */
@Repository
public interface MerchantPropertyBindingRepository extends JpaRepository<MerchantPropertyBinding, Long> {

    List<MerchantPropertyBinding> findByMerchantNo(String merchantNo);

    List<MerchantPropertyBinding> findByCompanyIdAndSplitModeAndStatus(Long companyId, Integer splitMode, Integer status);

    List<MerchantPropertyBinding> findByCompanyIdAndStatus(Long companyId, Integer status);

    Optional<MerchantPropertyBinding> findByMerchantNoAndSplitModeAndStatus(String merchantNo, Integer splitMode, Integer status);

    Optional<MerchantPropertyBinding> findByMerchantNoAndCompanyId(String merchantNo, Long companyId);
}
