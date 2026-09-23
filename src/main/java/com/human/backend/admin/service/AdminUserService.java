package com.human.backend.admin.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.admin.dto.request.AdminRoleUpdateRequest;
import com.human.backend.admin.dto.response.AdminUserResponse;
import com.human.backend.auth.entity.AppUser;
import com.human.backend.auth.repository.AppUserRepository;
import com.human.backend.global.exception.ApiException;

@Service
public class AdminUserService {

    private final AppUserRepository appUserRepository;

    public AdminUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * 전체 회원과 소속 시설의 기본 상태를 최신 가입 순으로 반환합니다.
     * 이 기능은 조회 전용이며 회원 수정이나 삭제는 수행하지 않습니다.
     */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> getUsers() {
        // facility를 함께 조회한 Entity 목록을 최신 가입 순으로 정렬한 뒤 안전한 응답 DTO로 변환합니다.
        return appUserRepository.findAll().stream()
            .sorted(Comparator.comparing(
                AppUser::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .map(AdminUserResponse::from)
            .toList();
    }

    /**
     * ADMIN이 선택한 사용자를 USER 또는 MANAGER로 변경합니다.
     * 최고 관리자 권한은 API로 새로 부여하지 않고 DB 운영 절차를 통해서만 관리합니다.
     */
    @Transactional
    public AdminUserResponse updateRole(Long actorUserId, Long targetUserId, AdminRoleUpdateRequest request) {
        // 현재 ADMIN이 실수로 자기 권한을 내려 관리자 화면에서 빠지는 상황을 막습니다.
        if (actorUserId.equals(targetUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_ROLE_CHANGE_NOT_ALLOWED",
                "자신의 관리자 권한은 이 화면에서 변경할 수 없습니다.");
        }
        // 이 API는 USER와 MANAGER 전환 전용입니다. ADMIN 추가는 별도 운영 절차로만 수행합니다.
        if (request.role() == AppUser.Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_ROLE_CHANGE_NOT_ALLOWED",
                "최고 관리자 권한은 이 화면에서 부여할 수 없습니다.");
        }

        // URL로 받은 대상 ID가 실제 DB에 있는지 확인하고 없으면 명확한 404 응답을 만듭니다.
        AppUser target = appUserRepository.findById(targetUserId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        // 다른 ADMIN 계정을 USER나 MANAGER로 강등하는 동작도 같은 API에서 금지합니다.
        if (target.getRole() == AppUser.Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_ROLE_CHANGE_NOT_ALLOWED",
                "다른 최고 관리자의 권한은 변경할 수 없습니다.");
        }

        // 트랜잭션 안에서 Entity를 변경하므로 JPA 변경 감지가 role UPDATE를 자동 실행합니다.
        target.changeRole(request.role());
        return AdminUserResponse.from(target);
    }
}
