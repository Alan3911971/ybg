package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyWoLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyWoLogRepository extends JpaRepository<PropertyWoLog, Long> {

    List<PropertyWoLog> findByWoIdOrderByCreateTimeAsc(Long woId);
}
