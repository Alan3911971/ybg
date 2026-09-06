package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MemberWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberWalletRepository extends JpaRepository<MemberWallet, Long> {

    /** 查询会员所有商家钱包 */
    List<MemberWallet> findAllByMemberIdOrderByStoreId(String memberId);

    /** 查询会员在指定商家的钱包 */
    Optional<MemberWallet> findByMemberIdAndStoreId(String memberId, String storeId);

    /** 查询会员在指定商家钱包，不存在则创建 */
    @Query("SELECT w FROM MemberWallet w WHERE w.memberId = :memberId AND w.storeId = :storeId")
    Optional<MemberWallet> findForUpdate(@Param("memberId") String memberId, @Param("storeId") String storeId);
}
