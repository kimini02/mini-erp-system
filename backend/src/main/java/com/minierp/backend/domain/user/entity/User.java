package com.minierp.backend.domain.user.entity;

import com.minierp.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Formula;
import java.time.LocalDate;
import java.util.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User extends BaseEntity {

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Column(name = "user_email", nullable = false, unique = true, length = 100)
    private String userEmail;

    @Column(name = "user_pw", length = 255)
    private String userPw;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;
    @Column(name = "position_id", nullable = false)
    private Long positionId;
    private LocalDate hireDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;
    @Column(name = "must_change_pw", nullable = false)
    private boolean mustChangePassword;
    @Column(nullable = false)
    private int tokenVersion;
    @Column(name = "assign_role", length = 30)
    private String assignRole;

    // Read-only compatibility projections; M1 프론트 작업 후 제거.
    @Formula("(select case d.code when 'D-0002' then '01' when 'D-0003' then '02' when 'D-0004' then '03' else d.code end from departments d where d.id=department_id)")
    private String departmentCode;
    @Formula("(select d.name from departments d where d.id=department_id)")
    private String departmentName;
    @Formula("(select p.name from positions p where p.id=position_id)")
    private String positionName;
    @Enumerated(EnumType.STRING)
    @Formula("(select case coalesce(max(case r.code when 'CHIEF' then 2 when 'LEADER' then 1 else 0 end),0) when 2 then 'ADMIN' when 1 then 'TEAM_LEADER' else 'USER' end from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=id)")
    private UserRole userRole;
    @Formula("(select group_concat(r.code) from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=id)")
    private String roleCodes;
    @ElementCollection
    @CollectionTable(name="user_roles", joinColumns=@JoinColumn(name="user_id"),
            uniqueConstraints=@UniqueConstraint(columnNames={"user_id","role_id"}))
    @Column(name="role_id", nullable=false)
    private Set<Long> roleIds = new HashSet<>();

    @Column(name = "total_annual_leave", nullable = false, precision = 5, scale = 1)
    private BigDecimal totalAnnualLeave;

    @Column(name = "used_annual_leave", nullable = false, precision = 5, scale = 1)
    private BigDecimal usedAnnualLeave;

    @Column(name = "remaining_annual_leave", nullable = false, precision = 5, scale = 1)
    private BigDecimal remainingAnnualLeave;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    private User(String userName, String loginId, String userEmail, String userPw, String departmentCode, String positionName, UserRole userRole) {
        this.userName = userName;
        this.loginId = loginId;
        this.userEmail = userEmail;
        this.userPw = userPw;
        this.departmentCode = departmentCode;
        this.departmentId = switch (departmentCode) { case "01" -> 2L; case "02" -> 3L; case "03" -> 4L; default -> throw new IllegalArgumentException("기존 부서 코드가 아닙니다."); };
        this.departmentName = DepartmentCode.fromCode(departmentCode).getDisplayName();
        this.positionName = positionName;
        this.positionId = switch (positionName) { case "대리" -> 2L; case "과장" -> 3L; case "팀장" -> 4L; case "관리소장", "관리자" -> 5L; default -> 1L; };
        this.assignRole = null;
        changeRole(userRole);
        this.status = UserStatus.ACTIVE;

        BigDecimal initialAnnualLeave = resolveInitialAnnualLeave(positionName);
        this.totalAnnualLeave = initialAnnualLeave;
        this.usedAnnualLeave = BigDecimal.ZERO;
        this.remainingAnnualLeave = initialAnnualLeave;
    }

    public static User create(String userName, String loginId, String userEmail, String encodedPassword, String departmentCode, String positionName) {
        return new User(userName, loginId, userEmail, encodedPassword, departmentCode, positionName, UserRole.USER);
    }

    public static User createInitialAdmin(String userName, String loginId, String userEmail, String encodedPassword) {
        User admin = new User(userName, loginId, userEmail, encodedPassword,
                DepartmentCode.values()[0].getCode(), "관리소장", UserRole.ADMIN);
        admin.departmentId = 1L;
        admin.departmentCode = "D-0001";
        admin.departmentName = "회사";
        admin.mustChangePassword = true;
        admin.totalAnnualLeave = BigDecimal.ZERO;
        admin.usedAnnualLeave = BigDecimal.ZERO;
        admin.remainingAnnualLeave = BigDecimal.ZERO;
        return admin;
    }

    public void updateProfile(String userName, String departmentCode, String positionName) {
        this.userName = userName;
        this.departmentCode = departmentCode;
        this.departmentId = switch (departmentCode) { case "01" -> 2L; case "02" -> 3L; case "03" -> 4L; default -> throw new IllegalArgumentException("기존 부서 코드가 아닙니다."); };
        this.departmentName = DepartmentCode.fromCode(departmentCode).getDisplayName();
        this.positionName = positionName;
        this.positionId = switch (positionName) { case "대리" -> 2L; case "과장" -> 3L; case "팀장" -> 4L; case "관리소장", "관리자" -> 5L; default -> 1L; };
    }

    public void changeRole(UserRole userRole) {
        this.userRole = userRole;
        // Reserved IDs belong to immutable seeded system roles; old domain fixtures use this adapter.
        this.roleIds.clear();
        this.roleIds.add(userRole == UserRole.ADMIN ? 1L : userRole == UserRole.TEAM_LEADER ? 2L : 3L);
        this.roleCodes = userRole == UserRole.ADMIN ? "CHIEF" : userRole == UserRole.TEAM_LEADER ? "LEADER" : "EMPLOYEE";
    }

    public boolean isActive() { return status == UserStatus.ACTIVE; }

    public List<String> roleCodeList() {
        return roleCodes == null ? List.of() : Arrays.stream(roleCodes.split(",")).sorted().toList();
    }

    public boolean passwordMatches(String encodedPassword) {
        return userPw.equals(encodedPassword);
    }

    public void updatePassword(String newEncodedPassword) {
        this.userPw = newEncodedPassword;
        this.mustChangePassword = false;
        this.tokenVersion++;
    }

    public void deductAnnualLeave(BigDecimal usedDays) {
        if (usedDays == null || usedDays.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("차감할 연차 일수가 유효하지 않습니다.");
        }
        if (remainingAnnualLeave.compareTo(usedDays) < 0) {
            throw new IllegalArgumentException("잔여 연차가 부족합니다.");
        }

        this.usedAnnualLeave = this.usedAnnualLeave.add(usedDays);
        this.remainingAnnualLeave = this.remainingAnnualLeave.subtract(usedDays);
    }

    public static BigDecimal resolveInitialAnnualLeave(String positionName) {
        if (positionName == null || positionName.isBlank()) {
            return BigDecimal.valueOf(14.0);
        }

        String normalized = positionName.replace(" ", "").trim();
        return switch (normalized) {
            case "사원" -> BigDecimal.valueOf(14.0);
            case "대리" -> BigDecimal.valueOf(16.0);
            case "과장" -> BigDecimal.valueOf(17.0);
            case "팀장" -> BigDecimal.valueOf(18.0);
            case "관리소장", "관리자" -> BigDecimal.valueOf(19.0);
            default -> BigDecimal.valueOf(14.0);
        };
    }
}
