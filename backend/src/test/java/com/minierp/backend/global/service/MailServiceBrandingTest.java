package com.minierp.backend.global.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MailServiceBrandingTest {
    @Test
    void allMailTypesUseWorkFlowBrand() {
        var sender = mock(JavaMailSender.class);
        var service = new MailService(sender);
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "fromAddress", "workflow@example.test");
        String code = "654321";
        String recipient = "fixture@example.test";

        service.sendVerificationCodeMail(recipient, code);
        service.sendInvitationMail(recipient, "fixture", "https://workflow.example.test/password/setup");
        service.sendFindIdMail(recipient, "fixture");

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(3)).send(captor.capture());
        var messages = captor.getAllValues();
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.getSubject()).startsWith("[WorkFlow] ").doesNotContain("Mini ERP");
            assertThat(message.getText()).doesNotContain("Mini ERP");
        });
        assertThat(messages.get(0).getText()).contains(code);
    }
}
