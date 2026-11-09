package com.minierp.backend.domain.project.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class ProjectStore {
    private final SqlStore db;

    public Map<String, Object> findLockedProject(long id) {
        return db.one("SELECT id FROM projects WHERE id=:id FOR UPDATE", map("id", id), ErrorCode.PROJECT_NOT_FOUND);
    }

    public long insertProject(
            String key,
            String title,
            String content,
            LocalDate start,
            LocalDate end,
            String priority,
            Long leader,
            LocalDateTime now) {
        return db.insert(
                "INSERT INTO projects(project_key,title,content,start_date,end_date,priority,status,leader_id,created_at) VALUES(:key,:title,:content,:start,:end,:priority,'READY',:leader,:now)",
                map("key", key, "title", title, "content", content, "start", start, "end", end,
                        "priority", priority, "leader", leader, "now", now));
    }

    public int insertLeaderMember(long projectId, Long userId, LocalDateTime now) {
        return db.update("INSERT INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)",
                map("p", projectId, "u", userId, "now", now));
    }

    public int insertLeaderIfMissing(long projectId, Long userId, LocalDateTime now) {
        return db.update("INSERT IGNORE INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)",
                map("p", projectId, "u", userId, "now", now));
    }

    public int updateProject(
            long id,
            String title,
            String content,
            LocalDate start,
            LocalDate end,
            String priority,
            Long leader,
            LocalDateTime now) {
        return db.update(
                "UPDATE projects SET title=COALESCE(:title,title),content=COALESCE(:content,content),start_date=:start,end_date=:end,priority=COALESCE(:priority,priority),leader_id=COALESCE(:leader,leader_id),updated_at=:now WHERE id=:id",
                map("id", id, "title", title, "content", content, "start", start, "end", end,
                        "priority", priority, "leader", leader, "now", now));
    }

    public int deleteMembers(long id, List<Long> removed) {
        return db.update("DELETE FROM project_members WHERE project_id=:id AND user_id IN (:removed)",
                map("id", id, "removed", removed));
    }

    public int insertMember(long projectId, long userId, LocalDateTime now) {
        return db.update("INSERT INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)",
                map("p", projectId, "u", userId, "now", now));
    }

    public int updateChangedAt(long id, LocalDateTime now) {
        return db.update("UPDATE projects SET updated_at=:now WHERE id=:id", map("id", id, "now", now));
    }
}
