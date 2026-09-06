package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * ChatMessage Repository：按“顾客手机+商家”拉取会话。
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 顾客端：拉取与某商家的全部消息（按 id 增序） */
    List<ChatMessage> findByUserPhoneAndMerchantNoOrderByIdAsc(String userPhone, String merchantNo);

    
    /** 商家端：某商家下全部顾客的未读计数 */
    long countByMerchantNoAndFromRoleAndIsRead(String merchantNo, String fromRole, Integer isRead);

    /** 商家端：某商家下全部会话最新一条 */
    List<ChatMessage> findFirstByMerchantNoOrderByIdDesc(String merchantNo);

    /** ???????????????????????????id??? */
    @org.springframework.data.jpa.repository.Query(
        "select m from ChatMessage m where m.id in " +
        "(select max(m2.id) from ChatMessage m2 where m2.merchantNo = :mno group by m2.userPhone) " +
        "order by m.id desc")
    List<ChatMessage> findSessionsByMerchantNo(@org.springframework.data.repository.query.Param("mno") String merchantNo);
}
