package com.ibigou.blindbox.service;

import com.ibigou.blindbox.repository.PropertyOwnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PropertyCarryOverService {

    private final PropertyOwnerRepository ownerRepository;

    @Scheduled(cron = "0 0 0 1 1 ?")
    @Transactional
    public void annualCarryOver() {
        List<?> owners = ownerRepository.findByStatusAndBalanceGreaterThan(1, BigDecimal.ZERO);
        for (Object obj : owners) {
            var owner = (com.ibigou.blindbox.entity.PropertyOwner) obj;
            owner.setCarryOver(owner.getCarryOver().add(owner.getBalance()));
            ownerRepository.save(owner);
        }
        log.info("Annual carry-over completed: {} owners processed", owners.size());
    }

    public BigDecimal getAvailableBalance(Long ownerId) {
        return ownerRepository.findById(ownerId)
                .map(owner -> owner.getBalance())
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getPendingSplit(Long ownerId) {
        return ownerRepository.findById(ownerId)
                .map(owner -> owner.getPendingSplit())
                .orElse(BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> getBalanceSummary(Long ownerId) {
        return ownerRepository.findById(ownerId)
                .map(owner -> {
                    Map<String, BigDecimal> summary = new HashMap<>();
                    summary.put("available", owner.getBalance());
                    summary.put("pending", owner.getPendingSplit());
                    summary.put("carryOver", owner.getCarryOver());
                    return summary;
                })
                .orElseGet(() -> {
                    Map<String, BigDecimal> summary = new HashMap<>();
                    summary.put("available", BigDecimal.ZERO);
                    summary.put("pending", BigDecimal.ZERO);
                    summary.put("carryOver", BigDecimal.ZERO);
                    return summary;
                });
    }
}
