package com.human.backend.facility.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.auth.entity.AppUser;
import com.human.backend.auth.repository.AppUserRepository;
import com.human.backend.facility.dto.request.FacilityRequest;
import com.human.backend.facility.dto.response.FacilityResponse;
import com.human.backend.facility.entity.Facility;
import com.human.backend.facility.repository.FacilityRepository;
import com.human.backend.global.exception.ApiException;

@Service
public class FacilityService {

    private final AppUserRepository appUserRepository;
    private final FacilityRepository facilityRepository;

    public FacilityService(AppUserRepository appUserRepository, FacilityRepository facilityRepository) {
        this.appUserRepository = appUserRepository;
        this.facilityRepository = facilityRepository;
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
        return FacilityResponse.from(facility);
    }

    @Transactional(readOnly = true)
    public FacilityResponse getMine(Long userId) {
        return FacilityResponse.from(getFacility(getUser(userId)));
    }

    @Transactional
    public FacilityResponse updateMine(Long userId, FacilityRequest request) {
        Facility facility = getFacility(getUser(userId));
        facility.update(request.name().trim(), request.facilityType().trim(), trimToNull(request.address()),
            trimToNull(request.contactName()), request.defaultMealCount(), request.breakfastMealCount(),
            request.lunchMealCount(), request.dinnerMealCount(), request.targetFoodCost());
        return FacilityResponse.from(facility);
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
