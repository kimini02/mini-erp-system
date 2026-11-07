package com.minierp.backend.domain.notification.service;

import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.domain.notification.repository.NotificationQueries;
import com.minierp.backend.domain.notification.repository.NotificationStore;
import com.minierp.backend.global.permission.PermissionAccess;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {
    private final NotificationQueries queries;
    private final NotificationStore store;
    private final PermissionAccess access;
    private final Clock clock;
    public Map<String, Object> list(boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        long user = access.actor();
        long offset = (long) page * size;
        long total = queries.countNotifications(user, size, offset, unreadOnly);
        var rows = queries.findNotifications(user, size, offset, unreadOnly)
                .stream()
                .map(n -> map("id", n.get("id"), "type", n.get("type"), "title", n.get("title"), "link", n.get("link"),
                        "read", n.get("read_at") != null, "createdAt", n.get("created_at")))
                .toList();
        return EmployeeService.page(rows, page, size, total);
    }

    public Map<String, Object> unreadCount() {
        return map("count", queries.countUnread(access.actor()));
    }

    @Transactional
    public void read(long id) {
        var n = queries.findOwner(id);
        if (number(n, "user_id") != access.actor()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        store.updateRead(id, access.actor(), LocalDateTime.now(clock));
    }

    @Transactional
    public void readAll() {
        store.updateAllRead(access.actor(), LocalDateTime.now(clock));
    }
}
