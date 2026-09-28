package com.human.backend.dashboard.service;

import com.human.backend.cost.dto.response.CostDriverResponse;
import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.dto.response.MenuRiskResponse;
import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MealPlanCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.cost.service.CostService;
import com.human.backend.dashboard.dto.response.DashboardMenuRiskRatioResponse;
import com.human.backend.dashboard.dto.response.DashboardRiskMenuDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static com.human.backend.cost.util.CostCalculationUtils.calculatePercentage;
import static com.human.backend.cost.util.CostConstants.DEFAULT_BASE_DATE;
import static com.human.backend.cost.util.CostConstants.DEFAULT_FACILITY_ID;

/**
 * [대시보드 원가 및 가격 위험 현황 요약 서비스]
 *
 * ■ 담당 요구사항:
 *   - DASH-005: 현재 식단 내 가격 위험 메뉴 비중을 요약한다.
 *
 * ■ 책임 분리 및 연동 구조 (Separation of Concerns):
 *   [DashboardService]
 *         │
 *         ├──> [CostRepository] (DB + CSV)
 *         │     - findMealPlansByFacilityAndDateRange() : 분석 대상 식단(끼니) 조회
 *         │     - findMenuIdsByPlanId()                 : 끼니별 편성 메뉴 ID 조회
 *         │     - findFacilityBudget()                  : 시설 기본 정보 조회
 *         │     - findMenuNameById()                    : 메뉴명 조회
 *         │
 *         └──> [CostService] (Facade)
 *               ├── calculateFutureMenuCost() : 식재료 예측단가 기반 메뉴 1인분 원가 산출
 *               ├── evaluateMenuRisk()        : 메뉴 원가 변동률 및 가격 위험등급(WARNING/CAUTION/SAFE) 진단
 *               └── identifyCostDrivers()     : 원가 상승을 견인하는 핵심 식재료(Cost Driver) 식별
 *
 * ■ 핵심 계산 원리:
 *   1. 대상 식단 식별: 기준일(baseDate)이 속한 주간(월~일 7일) 또는 일간 식단 목록 추출
 *   2. 식단 메뉴 및 위험도 수집:
 *      - 식단에 배치된 각 메뉴의 1인분 예상 단가, 누적 식수, 총 소요 식재료비 집계
 *      - CostService.evaluateMenuRisk()를 연동하여 메뉴별 위험 등급(WARNING/CAUTION/SAFE) 산출
 *      - 단일 요청 내 메뉴별 위험도/원가 캐싱(Local Caching)으로 중복 연산 방지
 *   3. 수량 기준 비중 산출:
 *      - 전체 메뉴 슬롯 건수 중 위험 메뉴(WARNING+CAUTION)의 건수 및 비율(%)
 *      - 중복 제외 고유 메뉴 기준 위험 메뉴 수 및 비율(%)
 *   4. 비용 기준 비중 산출:
 *      - 전체 식단 총 식재료비 중 위험 메뉴들이 차지하는 비용 총액 및 비용 비중(%)
 *   5. 종합 상태 진단 및 브리핑 생성:
 *      - 위험 메뉴 비용 비중 및 WARNING 발생 건수를 종합하여 CRITICAL/WARNING/CAUTION/SAFE 판정
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class DashboardService {

    private final CostRepository costRepository;
    private final CostService costService;

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,###");

    /**
     * [DASH-005] 현재 식단 내 가격 위험 메뉴 비중 요약 조회
     *
     * @param facilityId 시설 ID (null일 경우 기본 시설 ID 적용)
     * @param baseDate   분석 기준일 (null일 경우 기본 기준일 적용)
     * @param periodType 분석 기간 단위 ("WEEKLY" 또는 "DAILY", 기본값: "WEEKLY")
     * @return 대시보드 가격 위험 메뉴 비중 요약 응답 DTO
     */
    public DashboardMenuRiskRatioResponse summarizeMenuRiskRatio(Long facilityId, LocalDate baseDate, String periodType) {
        Long targetFacilityId = (facilityId != null) ? facilityId : DEFAULT_FACILITY_ID;
        LocalDate targetBaseDate = (baseDate != null) ? baseDate : DEFAULT_BASE_DATE;
        String normalizedPeriod = (periodType != null && periodType.equalsIgnoreCase("DAILY")) ? "DAILY" : "WEEKLY";

        // 1. 분석 기간 확정
        LocalDate startDate;
        LocalDate endDate;
        if ("DAILY".equals(normalizedPeriod)) {
            startDate = targetBaseDate;
            endDate = targetBaseDate;
        } else {
            startDate = targetBaseDate.with(DayOfWeek.MONDAY);
            endDate = startDate.plusDays(6);
        }

        // 2. 시설명 조회
        String facilityName = costRepository.findFacilityBudget(targetFacilityId, YearMonth.from(targetBaseDate))
                .map(FacilityBudgetVo::getFacilityName)
                .orElse("시설 " + targetFacilityId);

        log.info("==================================================");
        log.info(">> [DASH-005 식단 가격 위험 메뉴 비중 요약] 시설: {} (ID: {}), 기간: {} ~ {} ({})",
                facilityName, targetFacilityId, startDate, endDate, normalizedPeriod);

        // 3. 해당 기간 내 식단 목록 조회 (CostRepository 위임)
        List<MealPlanCostVo> mealPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId, startDate, endDate);

        if (mealPlans.isEmpty()) {
            log.warn(">> [DASH-005] 기간 내 편성된 식단이 없습니다. 빈 요약 결과를 반환합니다.");
            return buildEmptyResponse(targetFacilityId, facilityName, targetBaseDate, startDate, endDate, normalizedPeriod);
        }

        // 4. 식단에 편성된 메뉴별 정보 집계 (Local Cache로 중복 계산 최소화)
        Map<Long, MenuStatAccumulator> menuStatMap = new HashMap<>();
        Map<Long, MenuRiskResponse> riskCache = new HashMap<>();
        Map<Long, CostDriverResponse> driverCache = new HashMap<>();
        Map<Long, BigDecimal> unitCostCache = new HashMap<>();
        Map<Long, String> nameCache = new HashMap<>();

        int totalMenuSlotCount = 0; // 식단에 배치된 총 메뉴 제공 건수

        for (MealPlanCostVo plan : mealPlans) {
            List<Long> menuIds = costRepository.findMenuIdsByPlanId(plan.getPlanId());
            LocalDate planDate = plan.getPlanDate();
            int mealCount = plan.getMealCount();

            for (Long menuId : menuIds) {
                totalMenuSlotCount++;

                String menuName = nameCache.computeIfAbsent(menuId,
                        id -> costRepository.findMenuNameById(id).orElse("메뉴#" + id));

                // 1인분 예상 단가 조회 (캐싱)
                BigDecimal unitCost = unitCostCache.computeIfAbsent(menuId, id -> {
                    try {
                        MenuCostResponse costRes = costService.calculateFutureMenuCost(id, planDate, 1, null);
                        return (costRes != null && costRes.getCostPerPerson() != null)
                                ? costRes.getCostPerPerson()
                                : BigDecimal.ZERO;
                    } catch (Exception e) {
                        return BigDecimal.ZERO;
                    }
                });

                // 메뉴 위험도 조회 (캐싱)
                MenuRiskResponse riskRes = riskCache.computeIfAbsent(menuId, id -> {
                    try {
                        return costService.evaluateMenuRisk(id, planDate);
                    } catch (Exception e) {
                        return null;
                    }
                });

                // Cost Driver 조회 (캐싱)
                CostDriverResponse driverRes = driverCache.computeIfAbsent(menuId, id -> {
                    try {
                        return costService.identifyCostDrivers(id, planDate);
                    } catch (Exception e) {
                        return null;
                    }
                });

                BigDecimal lineCost = unitCost.multiply(BigDecimal.valueOf(mealCount)).setScale(0, RoundingMode.HALF_UP);

                // 누적 통계에 합산
                MenuStatAccumulator accumulator = menuStatMap.computeIfAbsent(menuId, id -> new MenuStatAccumulator(id, menuName));
                accumulator.appearanceCount += 1;
                accumulator.totalMealCount += mealCount;
                accumulator.totalCost = accumulator.totalCost.add(lineCost);
                accumulator.unitCost = unitCost;
                accumulator.riskResponse = riskRes;
                accumulator.driverResponse = driverRes;
            }
        }

        // 5. 전체 식단 총비용 집계
        BigDecimal totalPlannedCost = BigDecimal.ZERO;
        for (MenuStatAccumulator acc : menuStatMap.values()) {
            totalPlannedCost = totalPlannedCost.add(acc.totalCost);
        }

        // 6. 위험 등급별(WARNING, CAUTION, SAFE) 통계 분류
        int warningSlotCount = 0;
        int cautionSlotCount = 0;
        int safeSlotCount = 0;

        int uniqueWarningCount = 0;
        int uniqueCautionCount = 0;
        int uniqueSafeCount = 0;

        BigDecimal warningTotalCost = BigDecimal.ZERO;
        BigDecimal cautionTotalCost = BigDecimal.ZERO;
        BigDecimal safeTotalCost = BigDecimal.ZERO;

        List<DashboardRiskMenuDto> riskMenuList = new ArrayList<>();

        for (MenuStatAccumulator acc : menuStatMap.values()) {
            String riskLevel = "SAFE";
            int riskScore = 10;
            BigDecimal increaseRate = BigDecimal.ZERO;
            String riskReason = "가격 변동 안정";

            if (acc.riskResponse != null) {
                riskLevel = acc.riskResponse.getRiskLevel() != null ? acc.riskResponse.getRiskLevel() : "SAFE";
                riskScore = acc.riskResponse.getRiskScore() != null ? acc.riskResponse.getRiskScore() : 10;
                increaseRate = acc.riskResponse.getIncreaseRate() != null ? acc.riskResponse.getIncreaseRate() : BigDecimal.ZERO;
                riskReason = acc.riskResponse.getRiskSummary() != null ? acc.riskResponse.getRiskSummary() : riskReason;
            }

            // 핵심 식재료 상승 요인 요약
            String topCostDriver = null;
            if (acc.driverResponse != null && acc.driverResponse.getTopDriver() != null) {
                CostDriverResponse.IngredientDriver top = acc.driverResponse.getTopDriver();
                topCostDriver = String.format("%s (상승기여율 %s%%)", top.getIngredientName(), top.getContributionRate());
            }

            BigDecimal costRatio = calculatePercentage(acc.totalCost, totalPlannedCost);

            if ("WARNING".equalsIgnoreCase(riskLevel)) {
                warningSlotCount += acc.appearanceCount;
                uniqueWarningCount++;
                warningTotalCost = warningTotalCost.add(acc.totalCost);

                riskMenuList.add(DashboardRiskMenuDto.builder()
                        .menuId(acc.menuId)
                        .menuName(acc.menuName)
                        .riskLevel(riskLevel)
                        .riskScore(riskScore)
                        .costPerPerson(acc.unitCost)
                        .appearanceCount(acc.appearanceCount)
                        .totalMealCount(acc.totalMealCount)
                        .totalCost(acc.totalCost)
                        .costRatio(costRatio)
                        .increaseRate(increaseRate)
                        .topCostDriver(topCostDriver)
                        .riskReason(riskReason)
                        .build());
            } else if ("CAUTION".equalsIgnoreCase(riskLevel)) {
                cautionSlotCount += acc.appearanceCount;
                uniqueCautionCount++;
                cautionTotalCost = cautionTotalCost.add(acc.totalCost);

                riskMenuList.add(DashboardRiskMenuDto.builder()
                        .menuId(acc.menuId)
                        .menuName(acc.menuName)
                        .riskLevel(riskLevel)
                        .riskScore(riskScore)
                        .costPerPerson(acc.unitCost)
                        .appearanceCount(acc.appearanceCount)
                        .totalMealCount(acc.totalMealCount)
                        .totalCost(acc.totalCost)
                        .costRatio(costRatio)
                        .increaseRate(increaseRate)
                        .topCostDriver(topCostDriver)
                        .riskReason(riskReason)
                        .build());
            } else {
                safeSlotCount += acc.appearanceCount;
                uniqueSafeCount++;
                safeTotalCost = safeTotalCost.add(acc.totalCost);
            }
        }

        // 위험 메뉴 정렬 (WARNING 우선 -> 상승률 내림차순 -> 비용 내림차순)
        riskMenuList.sort((a, b) -> {
            boolean aWarn = "WARNING".equalsIgnoreCase(a.getRiskLevel());
            boolean bWarn = "WARNING".equalsIgnoreCase(b.getRiskLevel());
            if (aWarn != bWarn) {
                return aWarn ? -1 : 1;
            }
            int rateCompare = b.getIncreaseRate().compareTo(a.getIncreaseRate());
            if (rateCompare != 0) {
                return rateCompare;
            }
            return b.getTotalCost().compareTo(a.getTotalCost());
        });

        // 7. 비중(Ratio) 계산 (건수 기준 & 비용 기준)
        int totalUniqueMenuCount = menuStatMap.size();
        int totalRiskSlotCount = warningSlotCount + cautionSlotCount;
        int totalUniqueRiskCount = uniqueWarningCount + uniqueCautionCount;
        BigDecimal riskMenuTotalCost = warningTotalCost.add(cautionTotalCost);

        BigDecimal riskMenuRatio = calculateRatio(totalRiskSlotCount, totalMenuSlotCount);
        BigDecimal uniqueRiskMenuRatio = calculateRatio(totalUniqueRiskCount, totalUniqueMenuCount);
        BigDecimal warningRatio = calculateRatio(warningSlotCount, totalMenuSlotCount);
        BigDecimal cautionRatio = calculateRatio(cautionSlotCount, totalMenuSlotCount);
        BigDecimal safeRatio = calculateRatio(safeSlotCount, totalMenuSlotCount);

        BigDecimal riskCostRatio = calculatePercentage(riskMenuTotalCost, totalPlannedCost);
        BigDecimal warningCostRatio = calculatePercentage(warningTotalCost, totalPlannedCost);
        BigDecimal cautionCostRatio = calculatePercentage(cautionTotalCost, totalPlannedCost);
        BigDecimal safeCostRatio = calculatePercentage(safeTotalCost, totalPlannedCost);

        // 8. 대시보드 종합 위험 상태(overallRiskLevel) 및 브리핑 문구 생성
        String overallRiskLevel = determineOverallRiskLevel(riskCostRatio, uniqueWarningCount, uniqueCautionCount);
        String headline = buildHeadline(overallRiskLevel, riskMenuRatio, riskCostRatio);
        String summaryMessage = buildSummaryMessage(
                normalizedPeriod, startDate, endDate, totalMenuSlotCount, totalUniqueMenuCount,
                riskMenuRatio, riskCostRatio, riskMenuTotalCost, uniqueWarningCount, uniqueCautionCount, riskMenuList);

        log.info(">> [DASH-005 요약 완료] 총 메뉴 슬롯: {}건 (고유 {}개), 위험 메뉴: {}건 (비중 {}%, 비용 비중 {}%)",
                totalMenuSlotCount, totalUniqueMenuCount, totalRiskSlotCount, riskMenuRatio, riskCostRatio);
        log.info(">> 종합 위험 상태: {} | {}", overallRiskLevel, headline);
        log.info("==================================================");

        return DashboardMenuRiskRatioResponse.builder()
                .facilityId(targetFacilityId)
                .facilityName(facilityName)
                .baseDate(targetBaseDate)
                .startDate(startDate)
                .endDate(endDate)
                .periodType(normalizedPeriod)
                .totalMenuCount(totalMenuSlotCount)
                .uniqueMenuCount(totalUniqueMenuCount)
                .riskMenuCount(totalRiskSlotCount)
                .riskMenuRatio(riskMenuRatio)
                .uniqueRiskMenuCount(totalUniqueRiskCount)
                .uniqueRiskMenuRatio(uniqueRiskMenuRatio)
                .warningCount(warningSlotCount)
                .warningRatio(warningRatio)
                .cautionCount(cautionSlotCount)
                .cautionRatio(cautionRatio)
                .safeCount(safeSlotCount)
                .safeRatio(safeRatio)
                .totalPlannedCost(totalPlannedCost)
                .riskMenuTotalCost(riskMenuTotalCost)
                .riskCostRatio(riskCostRatio)
                .warningTotalCost(warningTotalCost)
                .warningCostRatio(warningCostRatio)
                .cautionTotalCost(cautionTotalCost)
                .cautionCostRatio(cautionCostRatio)
                .safeTotalCost(safeTotalCost)
                .safeCostRatio(safeCostRatio)
                .overallRiskLevel(overallRiskLevel)
                .summaryHeadline(headline)
                .summaryMessage(summaryMessage)
                .riskMenus(riskMenuList)
                .build();
    }

    /**
     * 비율(%) 계산 유틸 (소수점 1자리 반올림)
     */
    private BigDecimal calculateRatio(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    /**
     * 대시보드 종합 위험 상태 판정
     * - CRITICAL : 위험 메뉴 비용 비중 >= 30% 또는 경고(WARNING) 메뉴 >= 3개
     * - WARNING  : 위험 메뉴 비용 비중 >= 15% 또는 경고(WARNING) 메뉴 >= 1개
     * - CAUTION  : 위험 메뉴 비용 비중 >= 5% 또는 주의(CAUTION) 메뉴 >= 1개
     * - SAFE     : 그 외 안정세
     */
    private String determineOverallRiskLevel(BigDecimal riskCostRatio, int warningCount, int cautionCount) {
        if (riskCostRatio.compareTo(BigDecimal.valueOf(30)) >= 0 || warningCount >= 3) {
            return "CRITICAL";
        }
        if (riskCostRatio.compareTo(BigDecimal.valueOf(15)) >= 0 || warningCount >= 1) {
            return "WARNING";
        }
        if (riskCostRatio.compareTo(BigDecimal.valueOf(5)) >= 0 || cautionCount >= 1) {
            return "CAUTION";
        }
        return "SAFE";
    }

    /**
     * 대시보드 핵심 헤드라인 생성
     */
    private String buildHeadline(String overallRiskLevel, BigDecimal riskMenuRatio, BigDecimal riskCostRatio) {
        switch (overallRiskLevel) {
            case "CRITICAL":
                return String.format("식단 가격 위험 비중 %s%% (비용 %s%%) - 긴급 식단 재구성 권고", riskMenuRatio, riskCostRatio);
            case "WARNING":
                return String.format("식단 가격 위험 비중 %s%% (비용 %s%%) - 원가 경고 메뉴 점검 필요", riskMenuRatio, riskCostRatio);
            case "CAUTION":
                return String.format("식단 가격 위험 비중 %s%% (비용 %s%%) - 식재료 시세 모니터링 요망", riskMenuRatio, riskCostRatio);
            default:
                return "현재 식단 내 가격 위험 메뉴 없음 (안정 상태)";
        }
    }

    /**
     * 대시보드 브리핑 요약 메시지 생성
     */
    private String buildSummaryMessage(
            String periodType, LocalDate startDate, LocalDate endDate,
            int totalMenuSlotCount, int totalUniqueMenuCount,
            BigDecimal riskMenuRatio, BigDecimal riskCostRatio, BigDecimal riskMenuTotalCost,
            int uniqueWarningCount, int uniqueCautionCount,
            List<DashboardRiskMenuDto> riskMenuList) {

        String periodLabel = "DAILY".equals(periodType)
                ? String.format("당일 식단(%s)", startDate)
                : String.format("현재 주간 식단(%s ~ %s)", startDate, endDate);

        if (riskMenuList.isEmpty()) {
            return String.format("%s 내 모든 메뉴(%d개 슬롯, 고유 %d개)의 식재료 가격이 안정세(SAFE)를 유지하고 있어 원가 위험이 없습니다.",
                    periodLabel, totalMenuSlotCount, totalUniqueMenuCount);
        }

        String topRiskMenuNames = riskMenuList.stream()
                .limit(2)
                .map(DashboardRiskMenuDto::getMenuName)
                .collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s 기준 편성된 전체 메뉴 중 가격 위험 메뉴 비중은 건수 기준 %s%%, 식재료비 기준 %s%%(총 %s원)입니다. ",
                periodLabel, riskMenuRatio, riskCostRatio, MONEY_FORMAT.format(riskMenuTotalCost)));

        if (uniqueWarningCount > 0) {
            sb.append(String.format("특히 원가 급등(WARNING) 단계 메뉴 %d개('%s' 등)가 식단에 포함되어 있어 ",
                    uniqueWarningCount, topRiskMenuNames));
            sb.append("대체 식재료 활용 또는 식단 변경 검토가 권장됩니다.");
        } else if (uniqueCautionCount > 0) {
            sb.append(String.format("주의(CAUTION) 단계 메뉴 %d개('%s' 등)의 단가 상승세가 감지되었으므로 ",
                    uniqueCautionCount, topRiskMenuNames));
            sb.append("향후 식재료 시세 추이를 지속적으로 모니터링하시기 바랍니다.");
        }

        return sb.toString();
    }

    /**
     * 식단 데이터 미존재 시 빈 응답 객체 생성
     */
    private DashboardMenuRiskRatioResponse buildEmptyResponse(
            Long facilityId, String facilityName, LocalDate baseDate,
            LocalDate startDate, LocalDate endDate, String periodType) {
        return DashboardMenuRiskRatioResponse.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .baseDate(baseDate)
                .startDate(startDate)
                .endDate(endDate)
                .periodType(periodType)
                .totalMenuCount(0)
                .uniqueMenuCount(0)
                .riskMenuCount(0)
                .riskMenuRatio(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP))
                .uniqueRiskMenuCount(0)
                .uniqueRiskMenuRatio(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP))
                .warningCount(0)
                .warningRatio(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP))
                .cautionCount(0)
                .cautionRatio(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP))
                .safeCount(0)
                .safeRatio(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP))
                .totalPlannedCost(BigDecimal.ZERO)
                .riskMenuTotalCost(BigDecimal.ZERO)
                .riskCostRatio(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .warningTotalCost(BigDecimal.ZERO)
                .warningCostRatio(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .cautionTotalCost(BigDecimal.ZERO)
                .cautionCostRatio(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .safeTotalCost(BigDecimal.ZERO)
                .safeCostRatio(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .overallRiskLevel("SAFE")
                .summaryHeadline("편성된 식단 정보가 없습니다.")
                .summaryMessage("해당 기간에 등록된 식단 계획이 존재하지 않습니다.")
                .riskMenus(Collections.emptyList())
                .build();
    }

    /**
     * 식단 집계용 내부 누적기 (Accumulator)
     */
    private static class MenuStatAccumulator {
        final Long menuId;
        final String menuName;
        int appearanceCount = 0;
        int totalMealCount = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal unitCost = BigDecimal.ZERO;
        MenuRiskResponse riskResponse;
        CostDriverResponse driverResponse;

        MenuStatAccumulator(Long menuId, String menuName) {
            this.menuId = menuId;
            this.menuName = menuName;
        }
    }
}
