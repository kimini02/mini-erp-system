package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.domain.user.dto.LoginRequestDto;
import com.minierp.backend.domain.user.entity.DepartmentCode;
import com.minierp.backend.domain.user.entity.UserRole;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.domain.user.service.AuthService;
import com.minierp.backend.domain.user.service.InitialAdminInitializer;
import com.minierp.backend.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class InitialAdminMariaDbTest extends MariaDbIntegrationTest {
    private static final String PASSWORD = "Aa1!" + UUID.randomUUID().toString().substring(0, 12);
    @DynamicPropertySource
    static void adminProperties(DynamicPropertyRegistry registry) {
        registry.add("INITIAL_ADMIN_LOGIN_ID", () -> "bootstrap-admin");
        registry.add("INITIAL_ADMIN_NAME", () -> "최초 관리소장");
        registry.add("INITIAL_ADMIN_EMAIL", () -> "bootstrap@example.test");
        registry.add("INITIAL_ADMIN_PASSWORD", () -> PASSWORD);
    }

    @Autowired
    UserRepository repository;
    @Autowired
    InitialAdminInitializer initializer;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    AuthService authService;
    @Autowired
    ObjectMapper objectMapper;
    @Test
    void startupCreatesUsableAdminAndRepeatedStartupCallbackDoesNotDuplicate() {
        var admin = repository.findByLoginId("bootstrap-admin").orElseThrow();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(admin.getUserName()).isEqualTo("최초 관리소장");
        assertThat(admin.getUserEmail()).isEqualTo("bootstrap@example.test");
        assertThat(admin.getUserRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.roleCodeList()).containsExactly("CHIEF");
        assertThat(admin.getPositionName()).isEqualTo("관리소장");
        assertThat(admin.getDepartmentId()).isEqualTo(1L);
        assertThat(admin.getDepartmentCode()).isEqualTo("D-0001");
        assertThat(admin.getTotalAnnualLeave()).isZero();
        assertThat(admin.getUsedAnnualLeave()).isZero();
        assertThat(admin.getRemainingAnnualLeave()).isZero();
        assertThat(admin.getUserPw().equals(PASSWORD)).isFalse();
        assertThat(encoder.matches(PASSWORD, admin.getUserPw())).isTrue();
        var login = objectMapper.convertValue(Map.of("id", "bootstrap-admin", "password", PASSWORD),
                LoginRequestDto.class);
        assertThat(authService.login(login).getAccessToken()).isNotBlank();
        initializer.run(new DefaultApplicationArguments());
        var admins = repository.findByUserRole(UserRole.ADMIN);
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getId()).isEqualTo(admin.getId());
        assertThat(admins.get(0).getUserPw()).isEqualTo(admin.getUserPw());
    }
}
