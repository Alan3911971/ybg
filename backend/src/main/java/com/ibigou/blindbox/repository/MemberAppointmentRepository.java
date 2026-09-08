package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.MemberAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MemberAppointment Repository（预约）。
 */
public interface MemberAppointmentRepository extends JpaRepository<MemberAppointment, Long> {

    List<MemberAppointment> findByMerchantNoAndUserPhoneOrderByApptTimeDesc(String merchantNo, String userPhone);

    List<MemberAppointment> findByMerchantNoAndStatusOrderByApptTimeAsc(String merchantNo, Integer status);

    /** 待服务且未提醒且即将到点（提醒窗口内）的预约 */
    @Query("select a from MemberAppointment a where a.status = 0 and a.remindSent = 0 " +
           "and a.apptTime <= :deadline and a.apptTime > :now")
    List<MemberAppointment> findPendingRemind(@Param("now") LocalDateTime now, @Param("deadline") LocalDateTime deadline);

    @Modifying
    @Query("update MemberAppointment a set a.remindSent = 1 where a.id = :id")
    int markRemindSent(@Param("id") Long id);
}
