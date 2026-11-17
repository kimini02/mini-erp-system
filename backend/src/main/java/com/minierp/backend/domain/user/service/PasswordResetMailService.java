package com.minierp.backend.domain.user.service;

import com.minierp.backend.global.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Slf4j
public class PasswordResetMailService {
    private final TaskExecutor executor;
    private final MailService mail;

    public PasswordResetMailService(@Qualifier("accountRecoveryMailExecutor") TaskExecutor executor, MailService mail) {
        this.executor = executor;
        this.mail = mail;
    }

    public record Requested(String email, String verificationCode) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(Requested event) {
        try {
            executor.execute(() -> {
                try {
                    mail.sendVerificationCodeMail(event.email(), event.verificationCode());
                } catch (RuntimeException ignored) {
                    // SMTP exceptions may contain addresses, credentials or message bodies.
                    log.warn("비밀번호 재설정 메일 발송 실패: 수신되지 않으면 다시 요청해 주세요.");
                }
            });
        } catch (TaskRejectedException ignored) {
            // Keep the public response identical even when the bounded queue is full or shutting down.
            log.warn("비밀번호 재설정 메일 대기열 수용 실패: 잠시 후 다시 요청해 주세요.");
        }
    }
}
