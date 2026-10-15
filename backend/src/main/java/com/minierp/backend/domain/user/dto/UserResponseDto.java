package com.minierp.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minierp.backend.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponseDto {

    private final Long id;
    private final String name;
    private final String email;
    // M1 프론트 작업 후 제거: departmentCode, departmentName, position, role
    private final String departmentCode;
    private final String departmentName;
    private final String position;
    private final String role;
    private final BigDecimal remainingAnnualLeave;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private final String loginId;
    private final Department department;
    private final java.util.List<String> roles;
    private final java.time.LocalDate hireDate;
    private final String status;
    private final boolean invitationPending;
    public record Department(Long id, String name) {}

    public static UserResponseDto summary(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getUserName(),
                user.getUserEmail(),
                user.getDepartmentCode(),
                user.getDepartmentName(),
                user.getPositionName(),
                user.getUserRole().name(),
                null,
                null,
                null,
                user.getLoginId(), new Department(user.getDepartmentId(), user.getDepartmentName()),
                user.roleCodeList(), user.getHireDate(), user.getStatus().name(), user.getUserPw() == null
        );
    }

    public static UserResponseDto detail(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getUserName(),
                user.getUserEmail(),
                user.getDepartmentCode(),
                user.getDepartmentName(),
                user.getPositionName(),
                user.getUserRole().name(),
                user.getRemainingAnnualLeave(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLoginId(), new Department(user.getDepartmentId(), user.getDepartmentName()),
                user.roleCodeList(), user.getHireDate(), user.getStatus().name(), user.getUserPw() == null
        );
    }

    public static UserResponseDto from(User user) {
        return summary(user);
    }

    private static String getDepartmentName(String departmentCode) {
        if (departmentCode == null) {
            return null;
        }
        try {
            return com.minierp.backend.domain.user.entity.DepartmentCode.fromCode(departmentCode).getDisplayName();
        } catch (Exception e) {
            return null;
        }
    }
}
