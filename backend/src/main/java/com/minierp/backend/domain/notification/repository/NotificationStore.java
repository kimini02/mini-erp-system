package com.minierp.backend.domain.notification.repository;

import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class NotificationStore {
    private final SqlStore db;

    public int insertDocumentNotification(
            long user,
            long doc,
            String type,
            String title,
            String link,
            LocalDateTime now) {
        return db.update(
                "INSERT INTO notifications(user_id,document_id,type,title,link,created_at) VALUES(:user,:doc,:type,:title,:link,:now)",
                map("user", user, "doc", doc, "type", type, "title", title, "link", link, "now", now));
    }

    public int insertTaskNotification(long user, String title, String link, LocalDateTime now) {
        return db.update(
                "INSERT INTO notifications(user_id,document_id,type,title,link,created_at) VALUES(:user,NULL,'TASK_ASSIGNED',:title,:link,:now)",
                map("user", user, "title", title, "link", link, "now", now));
    }

    public int insertOverdueNotification(long user, String title, String link, LocalDateTime now) {
        return db.update(
                "INSERT INTO notifications(user_id,document_id,type,title,link,created_at) VALUES(:user,NULL,'TASK_OVERDUE',:title,:link,:now)",
                map("user", user, "title", title, "link", link, "now", now));
    }

    public int updateRead(long id, long user, LocalDateTime now) {
        return db.update("UPDATE notifications SET read_at=COALESCE(read_at,:now) WHERE id=:id AND user_id=:user",
                map("id", id, "user", user, "now", now));
    }

    public int updateAllRead(long user, LocalDateTime now) {
        return db.update("UPDATE notifications SET read_at=:now WHERE user_id=:user AND read_at IS NULL",
                map("user", user, "now", now));
    }
}
