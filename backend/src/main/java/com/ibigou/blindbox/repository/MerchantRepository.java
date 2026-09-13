package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Merchant Repository。
 */
public interface MerchantRepository extends JpaRepository<Merchant, String> {

    java.util.Optional<Merchant> findByLoginAccount(String loginAccount);
    java.util.Optional<Merchant> findByMerchantNo(String merchantNo);

    /** 商家列表：按状态+关键词(名称/地址/行业)+行业筛选，数据库分页查询 */
    @Query("SELECT m FROM Merchant m WHERE m.status = 1 " +
           "AND (:keyword IS NULL OR :keyword = '' OR m.merchantName LIKE %:keyword% OR m.address LIKE %:keyword% OR m.industry LIKE %:keyword%) " +
           "AND (:industry IS NULL OR :industry = '' OR m.industry = :industry) " +
           "ORDER BY m.createTime DESC")
    Page<Merchant> findActiveMerchants(@Param("keyword") String keyword,
                                       @Param("industry") String industry,
                                       Pageable pageable);

    /** 获取所有启用商家的行业分类（去重、排序） */
    @Query("SELECT DISTINCT m.industry FROM Merchant m WHERE m.status = 1 AND m.industry IS NOT NULL AND m.industry <> '' ORDER BY m.industry")
    List<String> findDistinctActiveIndustries();
}
