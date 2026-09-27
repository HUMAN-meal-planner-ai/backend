package com.human.backend.errorlog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.human.backend.errorlog.entity.ErrorLog;

/**
 * JpaRepository를 상속하면 save, findById, findAll 같은 기본 SQL을 직접 작성하지 않아도 됩니다.
 * 메서드 이름 findByResolved를 Spring Data JPA가 해석하여 resolved 조건 쿼리를 자동 생성합니다.
 */
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {
    Page<ErrorLog> findByResolved(boolean resolved, Pageable pageable);
}
