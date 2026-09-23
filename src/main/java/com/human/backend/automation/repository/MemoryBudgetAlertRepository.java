package com.human.backend.automation.repository;

import com.human.backend.automation.entity.BudgetAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * [AUTO-002] 인메모리 예산 초과 위험 경고 알림 저장소 구현체
 * 
 * 동시성을 보장하는 ConcurrentHashMap 기반으로 알림 내역을 관리합니다.
 */
@Slf4j
@Repository
@SuppressWarnings("null")
public class MemoryBudgetAlertRepository implements BudgetAlertRepository {

    private final Map<Long, BudgetAlert> storage = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1L);

    @Override
    public BudgetAlert save(BudgetAlert alert) {
        if (alert == null) {
            throw new IllegalArgumentException("저장할 알림 객체는 null일 수 없습니다.");
        }

        if (alert.getAlertId() == null) {
            alert.setAlertId(sequence.getAndIncrement());
        }

        storage.put(alert.getAlertId(), alert);
        log.debug(">> [BudgetAlert 저장] ID: {}, 시설 ID: {}, 위험도: {}, 초과금액: {}",
                alert.getAlertId(), alert.getFacilityId(), alert.getRiskLevel(), alert.getExceededAmount());
        return alert;
    }

    @Override
    public Optional<BudgetAlert> findById(Long alertId) {
        if (alertId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(alertId));
    }

    @Override
    public List<BudgetAlert> findAll() {
        return storage.values().stream()
                .sorted(Comparator.comparing(BudgetAlert::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(BudgetAlert::getAlertId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Override
    public List<BudgetAlert> findByFacilityId(Long facilityId) {
        if (facilityId == null) {
            return List.of();
        }
        return storage.values().stream()
                .filter(alert -> facilityId.equals(alert.getFacilityId()))
                .sorted(Comparator.comparing(BudgetAlert::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(BudgetAlert::getAlertId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Override
    public List<BudgetAlert> findUnreadByFacilityId(Long facilityId) {
        if (facilityId == null) {
            return List.of();
        }
        return storage.values().stream()
                .filter(alert -> facilityId.equals(alert.getFacilityId()) && !alert.isRead())
                .sorted(Comparator.comparing(BudgetAlert::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(BudgetAlert::getAlertId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Override
    public List<BudgetAlert> findByRiskLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return List.of();
        }
        return storage.values().stream()
                .filter(alert -> riskLevel.equalsIgnoreCase(alert.getRiskLevel()))
                .sorted(Comparator.comparing(BudgetAlert::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(BudgetAlert::getAlertId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Override
    public long countUnreadByFacilityId(Long facilityId) {
        if (facilityId == null) {
            return 0L;
        }
        return storage.values().stream()
                .filter(alert -> facilityId.equals(alert.getFacilityId()) && !alert.isRead())
                .count();
    }

    @Override
    public boolean deleteById(Long alertId) {
        if (alertId == null) {
            return false;
        }
        return storage.remove(alertId) != null;
    }

    @Override
    public void deleteByFacilityId(Long facilityId) {
        if (facilityId == null) {
            return;
        }
        storage.entrySet().removeIf(entry -> facilityId.equals(entry.getValue().getFacilityId()));
    }

    @Override
    public void clear() {
        storage.clear();
        sequence.set(1L);
    }
}
