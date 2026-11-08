package com.minierp.backend.domain.user.service;

import com.minierp.backend.domain.user.dto.FindIdRequestDto;
import com.minierp.backend.domain.user.dto.FindIdResponseDto;
import com.minierp.backend.domain.user.dto.LoginRequestDto;
import com.minierp.backend.domain.user.dto.LoginResponseDto;
import com.minierp.backend.domain.user.dto.PasswordResetConfirmDto;
import com.minierp.backend.domain.user.dto.PasswordResetConfirmResponseDto;
import com.minierp.backend.domain.user.dto.PasswordResetRequestDto;
import com.minierp.backend.domain.user.dto.PasswordResetRequestResponseDto;
import com.minierp.backend.domain.user.dto.PasswordResetVerifyDto;
import com.minierp.backend.domain.user.dto.PasswordResetVerifyResponseDto;
import com.minierp.backend.domain.user.dto.UserResponseDto;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.exception.BusinessException;
import com.minierp.backend.global.exception.ErrorCode;
import com.minierp.backend.global.security.JwtTokenProvider;
import com.minierp.backend.global.service.MailService;
import com.minierp.backend.global.service.PasswordResetStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SecureRandom verificationCodeRandom = new SecureRandom();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MailService mailService;
    private final PasswordResetStoreService passwordResetStoreService;
    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto requestDto) {
        User user = userRepository.findByLoginId(requestDto.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (user.getUserPw() == null || !passwordEncoder.matches(requestDto.getPassword(), user.getUserPw())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
        String accessToken = jwtTokenProvider.generateAccessToken(String.valueOf(user.getId()), user.getTokenVersion());
        return LoginResponseDto.of(accessToken, jwtTokenProvider.getAccessTokenExpirationSeconds(),
                UserResponseDto.from(user), user.isMustChangePassword());
    }

    @Transactional(readOnly = true)
    public FindIdResponseDto findId(FindIdRequestDto requestDto) {
        User user = userRepository.findByUserEmail(requestDto.getEmail())
                .filter(matchedUser -> matchedUser.getUserName().equals(requestDto.getName()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "해당하는 사용자를 찾을 수 없습니다."));
        return FindIdResponseDto.of(user.getLoginId());
    }

    @Transactional
    public PasswordResetRequestResponseDto requestPasswordReset(PasswordResetRequestDto requestDto) {
        long requestCount = passwordResetStoreService.incrementRequestCount(requestDto.getEmail());
        if (requestCount > passwordResetStoreService.getMaxRequestsPerMinute()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        userRepository.findByUserEmail(requestDto.getEmail()).ifPresent(user -> {
            String verificationCode = generateVerificationCode();
            passwordResetStoreService.saveVerificationCode(user.getUserEmail(), verificationCode);
            mailService.sendVerificationCodeMail(user.getUserEmail(), verificationCode);
        });
        // 계정 존재 여부와 관계없이 동일 응답
        return PasswordResetRequestResponseDto.success();
    }

    @Transactional
    public PasswordResetVerifyResponseDto verifyPasswordReset(PasswordResetVerifyDto requestDto) {
        User user = userRepository.findByUserEmail(requestDto.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_REQUEST));
        String savedCode = passwordResetStoreService.getVerificationCode(user.getUserEmail());
        if (savedCode == null) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED);
        }
        if (!savedCode.equals(requestDto.getVerificationCode())) {
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }
        passwordResetStoreService.removeVerificationCode(user.getUserEmail());
        String resetProof = UUID.randomUUID().toString();
        passwordResetStoreService.saveResetProof(resetProof, user.getUserEmail());
        return PasswordResetVerifyResponseDto.of(resetProof);
    }

    @Transactional
    public PasswordResetConfirmResponseDto confirmPasswordReset(PasswordResetConfirmDto requestDto) {
        if (!requestDto.getNewPassword().equals(requestDto.getNewPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "비밀번호 확인이 일치하지 않습니다.");
        }
        validatePasswordPolicy(requestDto.getNewPassword());
        String email = passwordResetStoreService.getEmailByResetProof(requestDto.getResetProof());
        if (email == null) {
            throw new BusinessException(ErrorCode.RESET_PROOF_INVALID);
        }
        User user = userRepository.findByUserEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_REQUEST));
        // 기존 비밀번호와 동일한지 확인 (BCrypt)
        if (passwordEncoder.matches(requestDto.getNewPassword(), user.getUserPw())) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "이전과 다른 비밀번호를 입력해주세요.");
        }
        String encodedPassword = passwordEncoder.encode(requestDto.getNewPassword());
        user.updatePassword(encodedPassword);
        passwordResetStoreService.removeResetProof(requestDto.getResetProof());
        return PasswordResetConfirmResponseDto.success();
    }

    private String generateVerificationCode() {
        int code = 100000 + verificationCodeRandom.nextInt(900000);
        return String.valueOf(code);
    }

    private void validatePasswordPolicy(String password) {
        com.minierp.backend.global.security.PasswordPolicy.validate(password);
    }

    @Transactional
    public LoginResponseDto changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getUserPw() == null || !passwordEncoder.matches(currentPassword, user.getUserPw())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        validatePasswordPolicy(newPassword);
        if (passwordEncoder.matches(newPassword, user.getUserPw())) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION);
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.flush();
        return LoginResponseDto.of(jwtTokenProvider.generateAccessToken(userId.toString(), user.getTokenVersion()),
                jwtTokenProvider.getAccessTokenExpirationSeconds(), UserResponseDto.from(user),
                user.isMustChangePassword());
    }
}
