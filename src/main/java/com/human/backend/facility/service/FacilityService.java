package com.human.backend.facility.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import com.human.backend.auth.entity.AppUser;
import com.human.backend.auth.repository.AppUserRepository;
import com.human.backend.facility.dto.request.FacilityRequest;
import com.human.backend.facility.dto.request.ExecutedAmountRequest;
import com.human.backend.facility.dto.response.FacilityResponse;
import com.human.backend.facility.dto.response.MonthlyBudgetResponse;
import com.human.backend.facility.entity.Facility;
import com.human.backend.facility.repository.FacilityRepository;
import com.human.backend.facility.repository.MonthlyBudgetRepository;
import com.human.backend.global.exception.ApiException;

@Service
public class FacilityService {

    private final AppUserRepository appUserRepository;
    private final FacilityRepository facilityRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public FacilityService(AppUserRepository appUserRepository, FacilityRepository facilityRepository,
            MonthlyBudgetRepository monthlyBudgetRepository) {
        this.appUserRepository = appUserRepository;
        this.facilityRepository = facilityRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
    }

    @Transactional
    public FacilityResponse createForUser(Long userId, FacilityRequest request) {
        AppUser user = getUser(userId);
        // 한 사용자가 여러 시설을 임의 생성해 소속 범위를 벗어나지 않도록 최초 등록만 허용합니다.
        if (user.getFacility() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "FACILITY_ALREADY_ASSIGNED", "이미 소속 시설이 등록되어 있습니다.");
        }

        // 시설을 먼저 저장해 생성된 facility_id를 확보한 뒤 현재 사용자에게 소속 시설로 연결합니다.
        Facility facility = facilityRepository.save(toNewEntity(request));
        user.assignFacility(facility);
        appUserRepository.save(user);
        YearMonth currentMonth = currentMonth();
        if (request.monthlyBudget() != null) {
            monthlyBudgetRepository.saveMonth(facility.getId(), currentMonth, request.monthlyBudget());
        }
        BigDecimal monthlyBudget = monthlyBudgetRepository.findBudget(facility.getId(), currentMonth).orElse(null);
        return FacilityResponse.from(facility, currentMonth, monthlyBudget);
    }

    @Transactional(readOnly = true)
    public FacilityResponse getMine(Long userId) {
        Facility facility = getFacility(getUser(userId));
        YearMonth currentMonth = currentMonth();
        return FacilityResponse.from(facility, currentMonth,
            monthlyBudgetRepository.findBudget(facility.getId(), currentMonth).orElse(null));
    }

    @Transactional
    public FacilityResponse updateMine(Long userId, FacilityRequest request) {
        // 영속 상태의 Facility를 변경하므로 트랜잭션 종료 시 JPA 변경 감지가 UPDATE를 실행합니다.
        Facility facility = getFacility(getUser(userId));
        facility.update(request.name().trim(), request.facilityType().trim(), trimToNull(request.address()),
            trimToNull(request.contactName()), request.defaultMealCount(), request.breakfastMealCount(),
            request.lunchMealCount(), request.dinnerMealCount(), request.targetFoodCost());
        YearMonth currentMonth = currentMonth();
        BigDecimal monthlyBudget = monthlyBudgetRepository.findBudget(facility.getId(), currentMonth).orElse(null);
        return FacilityResponse.from(facility, currentMonth, monthlyBudget);
    }

    @Transactional
    public FacilityResponse saveMonthlyBudget(Long userId, YearMonth month) {
        validateMonthlyBudgetMonth(month);
        Facility facility = getFacility(getUser(userId));
        int totalMealCount = monthlyBudgetRepository.findMonthlyMealCount(facility.getId(), month);
        if (totalMealCount < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MONTHLY_MEALS_NOT_FOUND", "해당 월에 저장된 식단이 없어 예산을 저장할 수 없습니다.");
        }

        BigDecimal amount = MonthlyBudgetCalculator.calculate(facility, totalMealCount);
        monthlyBudgetRepository.saveMonth(facility.getId(), month, amount);
        return FacilityResponse.from(facility, month, amount);
    }

    private AppUser getUser(Long userId) {
        return appUserRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private Facility getFacility(AppUser user) {
        if (user.getFacility() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FACILITY_NOT_FOUND", "등록된 시설이 없습니다.");
        }
        return user.getFacility();
    }

    private YearMonth currentMonth() {
        return YearMonth.now(ZoneId.of("Asia/Seoul"));
    }

    private Facility toNewEntity(FacilityRequest request) {
        // 필수 문자열의 바깥 공백을 제거하고 선택 문자열의 공백 입력은 null로 정규화합니다.
        return new Facility(request.name().trim(), request.facilityType().trim(), trimToNull(request.address()),
            trimToNull(request.contactName()), request.defaultMealCount(), request.breakfastMealCount(),
            request.lunchMealCount(), request.dinnerMealCount(), request.targetFoodCost());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Transactional(readOnly = true)
    public List<MonthlyBudgetResponse> getMyMonthlyBudgets(Long userId) {
        Facility facility = getFacility(getUser(userId));
        YearMonth currentMonth = currentMonth();
        return IntStream.range(0, 6)
            .mapToObj(offset -> {
                YearMonth month = currentMonth.plusMonths(offset);
                BigDecimal amount = monthlyBudgetRepository.findBudget(facility.getId(), month).orElse(null);
                BigDecimal executed = monthlyBudgetRepository.findExecutedAmount(facility.getId(), month).orElse(null);
                return new MonthlyBudgetResponse(month.toString(), amount, executed);
            })
            .toList();
    }

    @Transactional
    public MonthlyBudgetResponse updateMyMonthlyBudget(Long userId, YearMonth month, BigDecimal amount) {
        validateMonthlyBudgetMonth(month);
        Facility facility = getFacility(getUser(userId));
        monthlyBudgetRepository.saveMonth(facility.getId(), month, amount);
        BigDecimal executed = monthlyBudgetRepository.findExecutedAmount(facility.getId(), month).orElse(null);
        return new MonthlyBudgetResponse(month.toString(), amount, executed);
    }

    @Transactional
    public MonthlyBudgetResponse updateMyExecutedAmount(Long userId, YearMonth month, BigDecimal amount) {
        validateMonthlyBudgetMonth(month);
        Facility facility = getFacility(getUser(userId));
        monthlyBudgetRepository.saveExecutedAmount(facility.getId(), month, amount);
        BigDecimal budget = monthlyBudgetRepository.findBudget(facility.getId(), month).orElse(BigDecimal.ZERO);
        return new MonthlyBudgetResponse(month.toString(), budget, amount);
    }

    private void validateMonthlyBudgetMonth(YearMonth month) {
        YearMonth currentMonth = currentMonth();
        if (month.isBefore(currentMonth) || month.isAfter(currentMonth.plusMonths(5))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MONTHLY_BUDGET_MONTH_OUT_OF_RANGE",
                "월 예산은 이번 달부터 5개월 뒤까지 저장할 수 있습니다.");
        }
    }
}
