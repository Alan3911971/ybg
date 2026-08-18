package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.SysAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SysAlert Repository。
 */
public interface SysAlertRepository extends JpaRepository<SysAlert, Long> {

    List<SysAlert> findByOrderByAlertIdDesc();
}
