package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MerchantQueueTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantQueueTicketRepository extends JpaRepository<MerchantQueueTicket, Long> {

    List<MerchantQueueTicket> findByMerchantNoAndStatusOrderByCreateTimeAsc(String merchantNo, Integer status);

    Optional<MerchantQueueTicket> findFirstByMerchantNoAndStatusOrderByCreateTimeAsc(String merchantNo, Integer status);

    List<MerchantQueueTicket> findByMerchantNoAndPhoneAndStatusInOrderByCreateTimeDesc(String merchantNo, String phone, List<Integer> statuses);

    Optional<MerchantQueueTicket> findByMerchantNoAndTicketNo(String merchantNo, String ticketNo);

    long countByMerchantNoAndStatus(String merchantNo, Integer status);
}
