package com.minierp.backend.domain.notification.service;

import com.minierp.backend.domain.notification.repository.NotificationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationStore store;
    private final Clock clock;
    /** App notifications are DB state: never AFTER_COMMIT or REQUIRES_NEW. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void store(long user, long document, String type, String title) {
        store.insertDocumentNotification(user, document, type, title, "/approvals/" + document,
                LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void storeTask(long user, long task, String title, String link) {
        store.insertTaskNotification(user, title, link, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void storeTaskOverdue(long user, String title, String link) {
        store.insertOverdueNotification(user, title, link, LocalDateTime.now(clock));
    }
}
