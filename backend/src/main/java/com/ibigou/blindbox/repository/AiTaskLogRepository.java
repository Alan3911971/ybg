package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.AiTaskLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AiTaskLog Repository（AI 任务记录）。
 */
public interface AiTaskLogRepository extends JpaRepository<AiTaskLog, Long> {

    List<AiTaskLog> findByMerchantNoOrderByIdDesc(String merchantNo);

    List<AiTaskLog> findByMerchantNoAndBizTypeOrderByIdDesc(String merchantNo, String bizType);
}
