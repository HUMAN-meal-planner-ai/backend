package com.human.backend.manager.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.auth.entity.AppUser;
import com.human.backend.auth.repository.AppUserRepository;
import com.human.backend.global.exception.ApiException;
import com.human.backend.manager.dto.request.ManagerMemberStatusRequest;
import com.human.backend.manager.dto.response.ManagerMemberResponse;

@Service
public class ManagerService {

    private final AppUserRepository appUserRepository;

    public ManagerService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * 로그인한 시설 관리자와 동일한 시설에 속한 구성원만 반환합니다.
     * readOnly=true는 데이터를 수정하지 않는 조회 작업임을 JPA에 알려 불필요한 변경 감지를 줄입니다.
     * 프런트가 facilityId를 전달하게 하지 않고 로그인 사용자의 시설 ID를 사용해야 다른 시설 ID 조작을 막을 수 있습니다.
     */
    @Transactional(readOnly = true)
    public List<ManagerMemberResponse> getMyFacilityMembers(Long managerUserId) {
        AppUser manager = getManager(managerUserId);

        // Repository 단계부터 facility_id 조건을 사용하므로 다른 시설의 계정은 결과에 포함되지 않습니다.
        return appUserRepository.findAllByFacility_IdOrderByNameAsc(manager.getFacility().getId()).stream()
            // JPA Entity를 그대로 노출하지 않고 공개 가능한 필드만 가진 응답 DTO로 변환합니다.
            .map(ManagerMemberResponse::from)
            .toList();
    }

    /**
     * 자기 시설의 일반 사용자만 사용 중지하거나 다시 활성화합니다.
     * @Transactional 안에서 조회한 Entity의 status를 바꾸면 JPA 변경 감지가 UPDATE SQL을 실행합니다.
     * 따라서 별도의 save 호출이 없어도 트랜잭션이 정상 종료될 때 DB에 반영됩니다.
     */
    @Transactional
    public ManagerMemberResponse updateMemberStatus(
            Long managerUserId, Long memberUserId, ManagerMemberStatusRequest request) {
        AppUser manager = getManager(managerUserId);
        AppUser member = appUserRepository.findById(memberUserId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        // 대상에게 시설이 없거나 두 시설 ID가 다르면 다른 시설의 사용자이므로 즉시 거부합니다.
        boolean sameFacility = member.getFacility() != null
            && manager.getFacility().getId().equals(member.getFacility().getId());
        if (!sameFacility) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OTHER_FACILITY_MEMBER",
                "다른 시설의 사용자는 관리할 수 없습니다.");
        }
        // 시설 관리자가 다른 관리자나 최고 관리자를 중지하는 권한 상승·업무 방해를 막습니다.
        if (member.getRole() != AppUser.Role.USER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MANAGER_STATUS_CHANGE_NOT_ALLOWED",
                "일반 사용자 계정의 상태만 변경할 수 있습니다.");
        }

        // 위의 두 검사를 모두 통과한 같은 시설 USER에게만 요청 상태를 적용합니다.
        member.changeStatus(request.status());
        return ManagerMemberResponse.from(member);
    }

    /** 여러 공개 메서드가 공통으로 사용하는 로그인 사용자·시설 존재 확인 로직입니다. */
    private AppUser getManager(Long managerUserId) {
        AppUser manager = appUserRepository.findById(managerUserId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        if (manager.getFacility() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FACILITY_NOT_FOUND", "등록된 시설이 없습니다.");
        }
        return manager;
    }
}
