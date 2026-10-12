package com.minierp.backend.domain.user.service;

import com.minierp.backend.domain.user.entity.DepartmentCode;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.entity.UserRole;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.exception.BusinessException;
import com.minierp.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class InitialAdminInitializerTest {
    private UserRepository repository;
    private MockEnvironment environment;
    private BCryptPasswordEncoder encoder;
    private InitialAdminInitializer initializer;
    private String password;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        password = "Aa1!" + UUID.randomUUID().toString().substring(0,12);
        environment = new MockEnvironment()
                .withProperty("INITIAL_ADMIN_LOGIN_ID", "initial-admin")
                .withProperty("INITIAL_ADMIN_NAME", "초기 관리소장")
                .withProperty("INITIAL_ADMIN_EMAIL", "initial-admin@example.test")
                .withProperty("INITIAL_ADMIN_PASSWORD", password);
        initializer = new InitialAdminInitializer(repository, encoder, environment);
    }

    @Test
    void invalidPasswordOnlyWarns(CapturedOutput output) {
        String invalid = UUID.randomUUID().toString();
        environment.setProperty("INITIAL_ADMIN_PASSWORD", invalid);
        initializer.run(new DefaultApplicationArguments());
        verify(repository, never()).saveAndFlush(any());
        assertThat(output.getAll()).contains("최초 관리자 생성 생략");
        assertThat(output.getAll().contains(invalid)).isFalse();
    }

    @Test
    void existingAdminDoesNotReadCredentialsOrWriteAnything() {
        when(repository.existsByUserRole(UserRole.ADMIN)).thenReturn(true);
        var unreadEnvironment = mock(org.springframework.core.env.Environment.class);
        new InitialAdminInitializer(repository, encoder, unreadEnvironment)
                .run(new DefaultApplicationArguments());
        verify(repository).existsByUserRole(UserRole.ADMIN);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(unreadEnvironment);
    }

    @ParameterizedTest
    @CsvSource({
            "LOGIN_ID, missing", "NAME, missing", "EMAIL, missing", "PASSWORD, missing",
            "LOGIN_ID, blank", "NAME, blank", "EMAIL, blank", "PASSWORD, blank"
    })
    void missingOrBlankCredentialOnlyWarns(String key, String kind, CapturedOutput output) {
        if (kind.equals("missing")) {
            ((org.springframework.core.env.MapPropertySource) environment.getPropertySources().get("mockProperties")).getSource().remove("INITIAL_ADMIN_" + key);
        } else {
            environment.setProperty("INITIAL_ADMIN_" + key, "   ");
        }
        initializer.run(new DefaultApplicationArguments());
        verify(repository).existsByUserRole(UserRole.ADMIN);
        verifyNoMoreInteractions(repository);
        assertThat(output.getAll()).contains("최초 관리자 생성 생략");
        assertThat(output.getAll().contains(password)).isFalse();
    }

    @Test
    void createsAdminWithEncodedPasswordAndZeroLeave(CapturedOutput output) {
        initializer.run(new DefaultApplicationArguments());
        var captor = ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.getLoginId()).isEqualTo("initial-admin");
        assertThat(admin.getUserName()).isEqualTo("초기 관리소장");
        assertThat(admin.getUserEmail()).isEqualTo("initial-admin@example.test");
        assertThat(admin.getUserRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.roleCodeList()).containsExactly("CHIEF");
        assertThat(admin.getDepartmentCode()).isEqualTo(DepartmentCode.values()[0].getCode());
        assertThat(admin.getPositionName()).isEqualTo("관리소장");
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getTotalAnnualLeave()).isZero();
        assertThat(admin.getUsedAnnualLeave()).isZero();
        assertThat(admin.getRemainingAnnualLeave()).isZero();
        assertThat(admin.getUserPw().equals(password)).isFalse();
        assertThat(encoder.matches(password, admin.getUserPw())).isTrue();
        assertThat(output.getAll().contains(password)).isFalse();
    }

    @Test
    void duplicateLoginIdFailsWithoutChangingExistingAccount() {
        when(repository.existsByLoginId("initial-admin")).thenReturn(true);
        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_ID_ALREADY_EXISTS));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateEmailFailsWithoutChangingExistingAccount() {
        when(repository.existsByUserEmail("initial-admin@example.test")).thenReturn(true);
        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        verify(repository, never()).saveAndFlush(any());
    }
}
