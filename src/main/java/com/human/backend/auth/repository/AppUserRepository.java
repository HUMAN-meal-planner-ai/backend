package com.human.backend.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.human.backend.auth.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * 관리자 회원 목록에서는 소속 시설 이름도 함께 표시해야 합니다.
     * EntityGraph로 facility를 한 번에 조회해 회원마다 추가 SQL이 실행되는 문제를 줄입니다.
     */
    @Override
    @EntityGraph(attributePaths = "facility")
    List<AppUser> findAll();

    /**
     * 시설 관리자가 자기 시설에 소속된 구성원만 조회하도록 시설 ID로 범위를 제한합니다.
     * Spring Data JPA가 메서드 이름을 해석해 WHERE facility_id = ? ORDER BY name ASC 쿼리를 만듭니다.
     * `Facility_Id`의 밑줄은 AppUser.facility 관계 안의 id 필드를 조건으로 사용한다는 뜻입니다.
     */
    List<AppUser> findAllByFacility_IdOrderByNameAsc(Long facilityId);
}
