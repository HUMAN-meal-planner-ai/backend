package com.human.backend.auth.entity;

import java.time.Instant;

import com.human.backend.facility.entity.Facility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user", schema = "mealfit")
public class AppUser {

    public enum Role { USER, MANAGER, ADMIN }
    public enum Status { ACTIVE, DISABLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private Facility facility;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = Role.USER;
        this.status = Status.ACTIVE;
    }

    public void assignFacility(Facility facility) {
        this.facility = facility;
    }

    /**
     * 서비스 관리자가 승인한 계정의 역할을 변경할 때만 사용합니다.
     * 필드를 public으로 열지 않고 의미 있는 메서드로 변경 지점을 제한해 권한 변경 위치를 추적하기 쉽게 합니다.
     */
    public void changeRole(Role role) {
        this.role = role;
    }

    /**
     * 권한 범위를 확인한 관리 서비스에서 계정 사용 상태를 변경할 때 사용합니다.
     * DISABLED가 되면 JwtAuthenticationFilter의 isActive 검사에서 제외되어 기존 토큰으로도 인증되지 않습니다.
     */
    public void changeStatus(Status status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public Long getId() { return id; }
    public Facility getFacility() { return facility; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
