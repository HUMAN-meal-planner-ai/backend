package com.human.backend.cost.repository;

import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MenuIngredientCostVo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Cost 도메인에 필요한 CSV Mock 데이터 파일을 파싱하고 메모리 데이터 셋으로 로딩하는 전담 헬퍼 컴포넌트
 * [단일 책임 원칙 (SRP)]: 파일 I/O 및 문자열 파싱 책임을 분리하여 Repository의 응집도를 높입니다.
 */
@Slf4j
@Component
public class CostCsvDataLoader {

    @Getter
    public static class LoadedCostData {
        private final Map<Long, String> facilityNameMap;
        private final Map<Long, String> menuMap;
        private final Map<Long, PriceInfo> latestPriceMap;
        private final Map<String, BigDecimal> predictedPriceMap;
        private final Map<Long, List<MenuIngredientCostVo>> menuIngredientsMap;
        private final Map<Long, List<Long>> planMenuItemsMap;
        private final Map<String, FacilityBudgetVo> budgetMap;
        private final List<RawMealPlanInfo> rawMealPlanList;

        public LoadedCostData(Map<Long, String> facilityNameMap,
                              Map<Long, String> menuMap,
                              Map<Long, PriceInfo> latestPriceMap,
                              Map<String, BigDecimal> predictedPriceMap,
                              Map<Long, List<MenuIngredientCostVo>> menuIngredientsMap,
                              Map<Long, List<Long>> planMenuItemsMap,
                              Map<String, FacilityBudgetVo> budgetMap,
                              List<RawMealPlanInfo> rawMealPlanList) {
            this.facilityNameMap = facilityNameMap;
            this.menuMap = menuMap;
            this.latestPriceMap = latestPriceMap;
            this.predictedPriceMap = predictedPriceMap;
            this.menuIngredientsMap = menuIngredientsMap;
            this.planMenuItemsMap = planMenuItemsMap;
            this.budgetMap = budgetMap;
            this.rawMealPlanList = rawMealPlanList;
        }
    }

    public static class PriceInfo {
        private final LocalDate priceDate;
        private final BigDecimal standardUnitPrice;

        public PriceInfo(LocalDate priceDate, BigDecimal standardUnitPrice) {
            this.priceDate = priceDate;
            this.standardUnitPrice = standardUnitPrice;
        }

        public LocalDate getPriceDate() {
            return priceDate;
        }

        public BigDecimal getStandardUnitPrice() {
            return standardUnitPrice;
        }
    }

    public static class RawMealPlanInfo {
        private final Long planId;
        private final Long facilityId;
        private final LocalDate planDate;
        private final String mealType;
        private final Integer mealCount;
        private final BigDecimal fallbackCost;

        public RawMealPlanInfo(Long planId, Long facilityId, LocalDate planDate, String mealType, Integer mealCount, BigDecimal fallbackCost) {
            this.planId = planId;
            this.facilityId = facilityId;
            this.planDate = planDate;
            this.mealType = mealType;
            this.mealCount = mealCount;
            this.fallbackCost = fallbackCost;
        }

        public Long getPlanId() {
            return planId;
        }

        public Long getFacilityId() {
            return facilityId;
        }

        public LocalDate getPlanDate() {
            return planDate;
        }

        public String getMealType() {
            return mealType;
        }

        public Integer getMealCount() {
            return mealCount;
        }

        public BigDecimal getFallbackCost() {
            return fallbackCost;
        }
    }

    /**
     * CSV 디렉터리 경로로부터 모든 Cost 도메인 데이터를 파싱하여 로드합니다.
     */
    public LoadedCostData loadAll(String mockCsvPath) {
        log.info(">> [CostCsvDataLoader] CSV mock data 로딩 시작. 경로: {}", mockCsvPath);
        File baseDir = new File(mockCsvPath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.warn(">> [CostCsvDataLoader] 지정된 CSV 디렉터리를 찾을 수 없습니다: {}", mockCsvPath);
            return new LoadedCostData(
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyList()
            );
        }

        try {
            Map<Long, String> facilityNameMap = loadFacilityCsv(new File(baseDir, "facility.csv"));
            Map<Long, String> menuMap = loadMenuCsv(new File(baseDir, "menu.csv"));
            Map<Long, String> ingredientNameMap = loadIngredientCsv(new File(baseDir, "ingredient.csv"));
            Map<Long, PriceInfo> latestPriceMap = loadLatestIngredientPrices(new File(baseDir, "ingredient_price.csv"));
            Map<String, BigDecimal> predictedPriceMap = loadPricePredictions(new File(baseDir, "price_prediction.csv"));
            Map<Long, List<MenuIngredientCostVo>> menuIngredientsMap = loadMenuIngredients(
                    new File(baseDir, "menu_ingredient.csv"), ingredientNameMap, latestPriceMap
            );
            Map<Long, List<Long>> planMenuItemsMap = loadPlanMenuItems(new File(baseDir, "meal_plan_item.csv"));
            Map<String, FacilityBudgetVo> budgetMap = loadBudgetCsv(new File(baseDir, "monthly_budget.csv"), facilityNameMap);
            List<RawMealPlanInfo> rawMealPlanList = loadMealPlanCsv(new File(baseDir, "meal_plan.csv"));

            log.info(">> [CostCsvDataLoader] CSV mock data 로딩 완료: 메뉴 {}개, 메뉴식재료 {}개, 시설 {}개, 예산 {}개, 식단 {}개, 예측가격 {}개",
                    menuMap.size(), menuIngredientsMap.size(), facilityNameMap.size(), budgetMap.size(), rawMealPlanList.size(), predictedPriceMap.size());

            return new LoadedCostData(
                    facilityNameMap, menuMap, latestPriceMap, predictedPriceMap,
                    menuIngredientsMap, planMenuItemsMap, budgetMap, rawMealPlanList
            );
        } catch (Exception e) {
            log.error(">> [CostCsvDataLoader] CSV mock data 로딩 중 오류 발생: {}", e.getMessage(), e);
            return new LoadedCostData(
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyList()
            );
        }
    }

    private Map<Long, String> loadFacilityCsv(File file) {
        Map<Long, String> result = new HashMap<>();
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = parseCsvLine(line);
                if (tokens.length >= 2) {
                    Long facilityId = parseLongSafe(tokens[0]);
                    String name = tokens[1].trim();
                    if (facilityId != null && !name.isEmpty()) {
                        result.put(facilityId, name);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">> [CostCsvDataLoader] facility.csv 로딩 실패: {}", e.getMessage());
        }
        return result;
    }

    private Map<Long, String> loadMenuCsv(File file) {
        Map<Long, String> result = new HashMap<>();
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = parseCsvLine(line);
                if (tokens.length >= 3) {
                    Long menuId = parseLongSafe(tokens[0]);
                    String name = tokens[2].trim();
                    if (menuId != null && !name.isEmpty()) {
                        result.put(menuId, name);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">> [CostCsvDataLoader] menu.csv 로딩 실패: {}", e.getMessage());
        }
        return result;
    }

    private Map<Long, String> loadIngredientCsv(File file) {
        Map<Long, String> result = new HashMap<>();
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = parseCsvLine(line);
                if (tokens.length >= 3) {
                    Long ingredientId = parseLongSafe(tokens[0]);
                    String name = tokens[2].trim();
                    if (ingredientId != null && !name.isEmpty()) {
                        result.put(ingredientId, name);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">> [CostCsvDataLoader] ingredient.csv 로딩 실패: {}", e.getMessage());
        }
        return result;
    }

    private Map<Long, PriceInfo> loadLatestIngredientPrices(File file) {
        Map<Long, PriceInfo> result = new HashMap<>();
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = parseCsvLine(line);
                if (tokens.length >= 9) {
                    Long ingredientId = parseLongSafe(tokens[1]);
                    LocalDate priceDate = parseLocalDateSafe(tokens[2]);
                    BigDecimal price = parseBigDecimalSafe(tokens[8]); // standard_unit_price

                    if (ingredientId != null && price != null) {
                        PriceInfo existing = result.get(ingredientId);
                        if (existing == null || (priceDate != null && priceDate.isAfter(existing.priceDate))) {
                            result.put(ingredientId, new PriceInfo(priceDate, price));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">> [CostCsvDataLoader] ingredient_price.csv 로딩 실패: {}", e.getMessage());
        }
        return result;
    }

    private Map<String, BigDecimal> loadPricePredictions(File file) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = parseCsvLine(line);
                if (tokens.length >= 6) {
                    Long ingredientId = parseLongSafe(tokens[1]);
                    LocalDate targetDate = parseLocalDateSafe(tokens[3]); // target_date
                    BigDecimal predictedPrice = parseBigDecimalSafe(tokens[5]); // predicted_price

                    if (ingredientId != null && targetDate != null && predictedPrice != null) {
                        String key = targetDate + ":" + ingredientId;
                        result.put(key, predictedPrice);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">> [CostCsvDataLoader] price_prediction.csv 로딩 실패: {}", e.getMessage());
        }
        return result;
    }

    private Map<Long, List<MenuIngredientCostVo>> loadMenuIngredients(File file,
                                                                      Map<Long, String> ingredientNameMap,
                                                                      Map<Long, PriceInfo> latestPriceMap) {
        Map<Long, List<MenuIngredientCostVo>> result = new HashMap<>();
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = parseCsvLine(line);
                if (tokens.length >= 4) {
                    Long menuId = parseLongSafe(tokens[1]);
                    Long ingredientId = parseLongSafe(tokens[2]);
                    BigDecimal quantity = parseBigDecimalSafe(tokens[3]);

                    if (menuId != null && ingredientId != null && quantity != null) {
                        String ingredientName = ingredientNameMap.getOrDefault(ingredientId, "식재료#" + ingredientId);
                        PriceInfo priceInfo = latestPriceMap.get(ingredientId);
                        BigDecimal unitPrice = (priceInfo != null) ? priceInfo.standardUnitPrice : BigDecimal.ZERO;
                        LocalDate priceDate = (priceInfo != null) ? priceInfo.priceDate : LocalDate.now();

                        MenuIngredientCostVo vo = new MenuIngredientCostVo(
                                ingredientId, ingredientName, quantity, unitPrice, priceDate
                        );

                        result.computeIfAbsent(menuId, k -> new ArrayList<>()).add(vo);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">> [CostCsvDataLoader] menu_ingredient.csv 로딩 실패: {}", e.getMessage());
        }
        return result;
    }

    private Map<Long, List<Long>> loadPlanMenuItems(File file) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = parseCsvLine(line);
                if (tokens.length >= 3) {
                    Long planId = parseLongSafe(tokens[1]);
                    Long menuId = parseLongSafe(tokens[2]);
                    if (planId != null && menuId != null) {
                        result.computeIfAbsent(planId, k -> new ArrayList<>()).add(menuId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">> [CostCsvDataLoader] meal_plan_item.csv 로딩 실패: {}", e.getMessage());
        }
        return result;
    }

    private Map<String, FacilityBudgetVo> loadBudgetCsv(File file, Map<Long, String> facilityNameMap) {
        Map<String, FacilityBudgetVo> result = new HashMap<>();
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = parseCsvLine(line);
                if (tokens.length >= 4) {
                    Long facilityId = parseLongSafe(tokens[1]);
                    String monthStr = tokens[2].trim();
                    BigDecimal budgetAmount = parseBigDecimalSafe(tokens[3]);

                    if (facilityId != null && !monthStr.isEmpty() && budgetAmount != null) {
                        String ymStr = monthStr.length() >= 7 ? monthStr.substring(0, 7) : monthStr;
                        YearMonth budgetMonth = YearMonth.parse(ymStr);
                        String facilityName = facilityNameMap.getOrDefault(facilityId, "시설#" + facilityId);
                        FacilityBudgetVo vo = new FacilityBudgetVo(facilityId, facilityName, budgetMonth, budgetAmount);
                        result.put(facilityId + ":" + budgetMonth, vo);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">> [CostCsvDataLoader] monthly_budget.csv 로딩 실패: {}", e.getMessage());
        }
        return result;
    }

    private List<RawMealPlanInfo> loadMealPlanCsv(File file) {
        List<RawMealPlanInfo> result = new ArrayList<>();
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] tokens = parseCsvLine(line);
                if (tokens.length >= 7) {
                    Long planId = parseLongSafe(tokens[0]);
                    Long facilityId = parseLongSafe(tokens[1]);
                    // tokens[2]는 created_by
                    LocalDate planDate = parseLocalDateSafe(tokens[3]);
                    String mealType = tokens[4].trim();
                    Integer mealCount = parseIntSafe(tokens[5]);
                    BigDecimal fallbackCost = parseBigDecimalSafe(tokens[6]);

                    if (planId != null && facilityId != null && planDate != null) {
                        result.add(new RawMealPlanInfo(
                                planId, facilityId, planDate, mealType,
                                mealCount != null ? mealCount : 1,
                                fallbackCost != null ? fallbackCost : BigDecimal.ZERO
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">> [CostCsvDataLoader] meal_plan.csv 로딩 실패: {}", e.getMessage());
        }
        return result;
    }

    public static String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }

    public static BigDecimal parseBigDecimalSafe(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try {
            return new BigDecimal(val.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static Long parseLongSafe(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try {
            return Long.parseLong(val.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static Integer parseIntSafe(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDate parseLocalDateSafe(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(val.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
