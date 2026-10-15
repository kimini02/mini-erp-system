package com.minierp.backend.global.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class MailServiceLoggingTest {
    @Test void disabledMailNeverLogsVerificationCode(CapturedOutput output) {
        var sender=mock(JavaMailSender.class);
        var service=new MailService(sender);
        String code="654321";
        service.sendVerificationCodeMail("fixture@example.test",code);
        assertThat(output.getAll()).contains("메일 발송 비활성화").doesNotContain(code);
        verifyNoInteractions(sender);
    }
}
