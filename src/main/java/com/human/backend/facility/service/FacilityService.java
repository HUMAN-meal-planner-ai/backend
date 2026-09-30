package com.human.backend.facility.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;

import com.human.backend.auth.entity.AppUser;
import com.human.backend.auth.repository.AppUserRepository;
import com.human.backend.facility.dto.request.FacilityRequest;
import com.human.backend.facility.dto.response.FacilityResponse;
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
        if (user.getFacility() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "FACILITY_ALREADY_ASSIGNED", "이미 소속 시설이 등록되어 있습니다.");
        }

        Facility facility = facilityRepository.save(toNewEntity(request));
        user.assignFacility(facility);
        appUserRepository.save(user);
        YearMonth currentMonth = currentMonth();
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
}
