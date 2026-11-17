package com.minierp.backend.domain.user.service;

import com.minierp.backend.domain.user.dto.PasswordResetRequestDto;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.security.JwtTokenProvider;
import com.minierp.backend.global.service.PasswordResetStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.security.SecureRandom;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthVerificationCodeTest {
    @Test
    void resetUsesSecureRandomAndKeepsSixDigitCodeInStoreAndMailEvent() {
        var users = mock(UserRepository.class);
        var store = mock(PasswordResetStoreService.class);
        var events = mock(org.springframework.context.ApplicationEventPublisher.class);
        String email = "reset@example.test";
        var user = mock(User.class);
        when(user.getUserEmail()).thenReturn(email);
        when(users.findByUserEmail(email)).thenReturn(Optional.of(user));
        when(store.incrementRequestCount(email)).thenReturn(1L, 2L);
        when(store.getMaxRequestsPerMinute()).thenReturn(5L);
        var request = new PasswordResetRequestDto();
        ReflectionTestUtils.setField(request, "email", email);
        // Verify the cryptographic source and both six-digit boundaries without
        // probabilistic assertions about output uniqueness or entropy.
        try (var random = mockConstruction(SecureRandom.class,
                (generator, context) -> when(generator.nextInt(900_000)).thenReturn(0, 899_999))) {
            var service = new AuthService(users, mock(PasswordEncoder.class), mock(JwtTokenProvider.class),
                    store, events);
            service.requestPasswordReset(request);
            service.requestPasswordReset(request);
            assertThat(random.constructed()).hasSize(1);
            verify(random.constructed().get(0), times(2)).nextInt(900_000);
            verify(store).saveVerificationCode(email, "100000");
            verify(store).saveVerificationCode(email, "999999");
            verify(events).publishEvent(new PasswordResetMailService.Requested(email, "100000"));
            verify(events).publishEvent(new PasswordResetMailService.Requested(email, "999999"));
        }
    }
}
