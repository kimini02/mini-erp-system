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
public class FindIdMailService {
    private final TaskExecutor executor;
    private final MailService mail;

    public FindIdMailService(@Qualifier("accountRecoveryMailExecutor") TaskExecutor executor, MailService mail) {
        this.executor = executor;
        this.mail = mail;
    }

    public record Requested(String email, String loginId) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(Requested event) {
        try {
            executor.execute(() -> {
                try {
                    mail.sendFindIdMail(event.email(), event.loginId());
                } catch (RuntimeException ignored) {
                    // SMTP exceptions may contain addresses, credentials or message bodies.
                    log.warn("아이디 안내 메일 발송 실패: 수신되지 않으면 다시 요청해 주세요.");
                }
            });
        } catch (TaskRejectedException ignored) {
            // Keep the public response identical even when the bounded queue is full or shutting down.
            log.warn("아이디 안내 메일 대기열 수용 실패: 잠시 후 다시 요청해 주세요.");
        }
    }
}
