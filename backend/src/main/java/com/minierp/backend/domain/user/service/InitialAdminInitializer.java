package com.minierp.backend.domain.user.service;

import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.entity.UserRole;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.exception.BusinessException;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitialAdminInitializer implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUserRole(UserRole.ADMIN)) {
            return;
        }
        String loginId = environment.getProperty("INITIAL_ADMIN_LOGIN_ID");
        String name = environment.getProperty("INITIAL_ADMIN_NAME");
        String email = environment.getProperty("INITIAL_ADMIN_EMAIL");
        String password = environment.getProperty("INITIAL_ADMIN_PASSWORD");
        if (!StringUtils.hasText(loginId) || !StringUtils.hasText(name) || !StringUtils.hasText(email)
                || !StringUtils.hasText(password)) {
            log.warn(
                    "최초 관리자 생성 생략: INITIAL_ADMIN_LOGIN_ID, INITIAL_ADMIN_NAME, "
                            + "INITIAL_ADMIN_EMAIL, INITIAL_ADMIN_PASSWORD 환경변수를 모두 설정해야 합니다.");
            return;
        }
        if (!com.minierp.backend.global.security.PasswordPolicy.accepts(password)) {
            log.warn("최초 관리자 생성 생략: 초기 비밀번호가 비밀번호 정책을 만족하지 않습니다.");
            return;
        }
        // Never elevate an existing non-admin account or overwrite its password.
        if (userRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.LOGIN_ID_ALREADY_EXISTS);
        }
        if (userRepository.existsByUserEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        userRepository.saveAndFlush(User.createInitialAdmin(name, loginId, email, passwordEncoder.encode(password)));
    }
}
