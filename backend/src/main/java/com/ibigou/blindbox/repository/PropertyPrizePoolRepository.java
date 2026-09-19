package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyPrizePool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyPrizePoolRepository extends JpaRepository<PropertyPrizePool, Long> {

    List<PropertyPrizePool> findByCompanyIdOrderByChannelAscPoolTypeAscCreateTimeDesc(Long companyId);

    List<PropertyPrizePool> findByCompanyIdAndChannelOrderByWeightDesc(Long companyId, Integer channel);

    List<PropertyPrizePool> findByCompanyIdAndPoolTypeAndChannelOrderByWeightDesc(Long companyId, Integer poolType, Integer channel);
}
