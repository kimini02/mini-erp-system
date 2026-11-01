package com.minierp.backend.domain.user.controller;

import com.minierp.backend.domain.user.service.AuthService;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.domain.user.dto.UserResponseDto;
import com.minierp.backend.global.permission.PermissionAccess;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {
    private final AuthService auth;
    private final UserRepository users;
    private final PermissionAccess permissions;
    private final Clock clock;
    public record PasswordChange(
            @NotBlank
            String currentPassword,
            @NotBlank
            String newPassword) {
    }

    @GetMapping
    public ApiResponse<?> me() {
        return ApiResponse.success(clock, UserResponseDto
                .from(users.findById(permissions.actor())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))));
    }

    @GetMapping("/permissions")
    public ApiResponse<?> permissions() {
        return ApiResponse.success(clock, permissions.grants(permissions.actor()).values().stream()
                .filter(g -> !g.actions().isEmpty()).toList());
    }

    @PatchMapping("/password")
    public ApiResponse<?> password(
            @Valid
            @RequestBody
            PasswordChange r) {
        return ApiResponse.success(clock, auth
                .changePassword(permissions.actor(), r.currentPassword(), r.newPassword()));
    }
}
