package com.minierp.backend.domain.notification.service;

import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.global.persistence.SqlStore;
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
    private final SqlStore db;
    private final PermissionAccess access;
    private final Clock clock;
    public Map<String, Object> list(boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var args = map("user", access.actor(), "size", size, "offset", (long) page * size);
        String where = "user_id=:user" + (unreadOnly ? " AND read_at IS NULL" : "");
        long total = db.count("SELECT COUNT(*) FROM notifications WHERE " + where, args);
        var rows = db.rows("SELECT id,type,title,link,read_at,created_at FROM notifications WHERE " + where
                + " ORDER BY id DESC LIMIT :size OFFSET :offset", args).stream()
                .map(n -> map("id", n.get("id"), "type", n.get("type"), "title", n.get("title"), "link", n.get("link"),
                "read", n.get("read_at") != null, "createdAt", n.get("created_at"))).toList();
        return EmployeeService.page(rows, page, size, total);
    }

    public Map<String, Object> unreadCount() {
        return map("count", db.count("SELECT COUNT(*) FROM notifications WHERE user_id=:user AND read_at IS NULL",
                map("user", access.actor())));
    }

    @Transactional
    public void read(long id) {
        var n = db.one("SELECT user_id FROM notifications WHERE id=:id", map("id", id), ErrorCode.RESOURCE_NOT_FOUND);
        if (number(n, "user_id") != access.actor()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        db.update("UPDATE notifications SET read_at=COALESCE(read_at,:now) WHERE id=:id AND user_id=:user",
                map("id", id, "user", access.actor(), "now", LocalDateTime.now(clock)));
    }

    @Transactional
    public void readAll() {
        db.update("UPDATE notifications SET read_at=:now WHERE user_id=:user AND read_at IS NULL",
                map("user", access.actor(), "now", LocalDateTime.now(clock)));
    }
}
