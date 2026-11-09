package com.minierp.backend.domain.notification.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class NotificationQueries {
    private final SqlStore db;

    public long countUnread(long user) {
        return db.count("SELECT COUNT(*) FROM notifications WHERE user_id=:user AND read_at IS NULL",
                map("user", user));
    }

    public Map<String, Object> findOwner(long id) {
        return db.one("SELECT user_id FROM notifications WHERE id=:id", map("id", id), ErrorCode.RESOURCE_NOT_FOUND);
    }

    public long countNotifications(long user, int size, long offset, boolean unreadOnly) {
        var args = parameters(user, size, offset);
        String where = where(unreadOnly);
        return db.count("SELECT COUNT(*) FROM notifications WHERE " + where, args);
    }

    public List<Map<String, Object>> findNotifications(long user, int size, long offset, boolean unreadOnly) {
        var args = parameters(user, size, offset);
        String where = where(unreadOnly);
        return db.rows("SELECT id,type,title,link,read_at,created_at FROM notifications WHERE " + where
                        + " ORDER BY id DESC LIMIT :size OFFSET :offset",
                args);
    }

    private Map<String, Object> parameters(long user, int size, long offset) {
        return map("user", user, "size", size, "offset", offset);
    }

    private String where(boolean unreadOnly) {
        return "user_id=:user" + (unreadOnly ? " AND read_at IS NULL" : "");
    }

}
