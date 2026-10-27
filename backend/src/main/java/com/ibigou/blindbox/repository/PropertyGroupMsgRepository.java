package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyGroupMsg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PropertyGroupMsgRepository extends JpaRepository<PropertyGroupMsg, Long> {

    List<PropertyGroupMsg> findByCompanyIdOrderByMsgTimeDesc(Long companyId);

    List<PropertyGroupMsg> findByCompanyIdAndReceiptStatusOrderByMsgTimeDesc(Long companyId, Integer receiptStatus);

    List<PropertyGroupMsg> findByGroupIdOrderByMsgTimeAsc(Long groupId);

    long countByCompanyIdAndIsDemandAndMsgTimeAfter(Long companyId, Integer isDemand, LocalDateTime after);

    long countByCompanyIdAndReceiptStatusAndMsgTimeAfter(Long companyId, Integer receiptStatus, LocalDateTime after);

    /** 同题并单：同群、同意图、已建单、时间窗内 */
    @Query("select m from PropertyGroupMsg m where m.groupId = :groupId and m.intentLabel = :intent "
            + "and m.woId is not null and m.msgTime >= :after order by m.msgId desc")
    List<PropertyGroupMsg> findMergeable(@Param("groupId") Long groupId,
                                         @Param("intent") String intent,
                                         @Param("after") LocalDateTime after);
}
