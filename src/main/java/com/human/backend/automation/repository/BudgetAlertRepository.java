package com.human.backend.automation.repository;

import com.human.backend.automation.entity.BudgetAlert;

import java.util.List;
import java.util.Optional;

/**
 * [AUTO-002] 예산 초과 위험 경고 알림 저장소 인터페이스
 * 
 * 알림 데이터의 저장, 조회, 읽음 처리 및 삭제 기능을 정의합니다.
 */
public interface BudgetAlertRepository {

    /**
     * 알림 저장 및 수정
     */
    BudgetAlert save(BudgetAlert alert);

    /**
     * 알림 단건 조회
     */
    Optional<BudgetAlert> findById(Long alertId);

    /**
     * 전체 알림 목록 조회 (최신순)
     */
    List<BudgetAlert> findAll();

    /**
     * 특정 시설의 알림 목록 조회 (최신순)
     */
    List<BudgetAlert> findByFacilityId(Long facilityId);

    /**
     * 특정 시설의 미확인(읽지 않은) 알림 목록 조회 (최신순)
     */
    List<BudgetAlert> findUnreadByFacilityId(Long facilityId);

    /**
     * 특정 위험 등급(WARNING, CAUTION, SAFE)의 알림 목록 조회
     */
    List<BudgetAlert> findByRiskLevel(String riskLevel);

    /**
     * 특정 시설의 미확인 알림 건수 조회
     */
    long countUnreadByFacilityId(Long facilityId);

    /**
     * 알림 삭제
     */
    boolean deleteById(Long alertId);

    /**
     * 특정 시설의 모든 알림 삭제
     */
    void deleteByFacilityId(Long facilityId);

    /**
     * 저장소 전체 초기화
     */
    void clear();
}
