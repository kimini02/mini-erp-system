package com.minierp.backend.domain.org.service;

import com.minierp.backend.domain.org.dto.OrgRequests.*;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.permission.PermissionAccess;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgService {
    private final SqlStore db;
    private final com.minierp.backend.domain.org.repository.DepartmentTreeLock treeLock;
    private final JdbcTemplate jdbc;
    private final PermissionAccess permissions;
    private Map<String, Object> department(long id) {
        return db.one("SELECT * FROM departments WHERE id=:id", map("id", id), ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    private void lockTree() {
        treeLock.lock();
    }

    public Map<String, Object> tree() {
        // Exactly two queries regardless of department count, including heads/positions.
        var departments = jdbc.queryForList("""
            SELECT d.id,d.name,d.code,d.parent_id,d.head_user_id,h.user_name AS head_name,p.name AS head_position
            FROM departments d LEFT JOIN users h ON h.id=d.head_user_id LEFT JOIN positions p ON p.id=h.position_id
            ORDER BY d.sort_order,d.id
            """);
        var counts = jdbc.queryForList("SELECT department_id,COUNT(*) AS members FROM users GROUP BY department_id");
        Map<Long, Long> members = new HashMap<>();
        for (var row : counts) {
            members.put(number(row, "department_id"), number(row, "members"));
        }
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (var row : departments) {
            long id = number(row, "id");
            Object head = row.get("head_user_id") == null ? null
                    : map("id", row.get("head_user_id"), "name", row.get("head_name"), "position", row
                    .get("head_position"));
            nodes.put(id, map("id", id, "name", row.get("name"), "code", row.get("code"), "head", head,
                    "directMemberCount", members.getOrDefault(id, 0L), "totalMemberCount", 0L, "children",
                    new ArrayList<Map<String, Object>>()));
        }
        Map<String, Object> root = null;
        for (var row : departments) {
            var node = nodes.get(number(row, "id"));
            if (row.get("parent_id") == null) {
                root = node;
            } else {
                children(nodes.get(number(row, "parent_id"))).add(node);
            }
        }
        if (root != null) {
            aggregate(root);
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> children(Map<String, Object> n) {
        return (List<Map<String, Object>>) n.get("children");
    }

    private long aggregate(Map<String, Object> node) {
        long total = number(node, "directMemberCount");
        for (var child : children(node)) {
            total += aggregate(child);
        }
        node.put("totalMemberCount", total);
        return total;
    }

    private Object head(Object userId, boolean includeDepartment) {
        if (userId == null) {
            return null;
        }
        var h = db.one(
                "SELECT u.id,u.user_name AS name,p.name AS position,d.name AS departmentName FROM users u JOIN positions p ON p.id=u.position_id JOIN departments d ON d.id=u.department_id WHERE u.id=:id",
                map("id", userId), ErrorCode.USER_NOT_FOUND);
        if (!includeDepartment) {
            h.remove("departmentName");
        }
        return h;
    }

    public Map<String, Object> detail(long id) {
        var d = department(id);
        List<Map<String, Object>> path = new ArrayList<>();
        Object fallback = null;
        Object parent = d.get("parent_id");
        while (parent != null) {
            var ancestor = department(((Number) parent).longValue());
            path.add(0, map("id", ancestor.get("id"), "name", ancestor.get("name")));
            if (fallback == null && ancestor.get("head_user_id") != null) {
                fallback = head(ancestor.get("head_user_id"), true);
            }
            parent = ancestor.get("parent_id");
        }
        var schedule = d.get("work_schedule_id") == null ? defaultSchedule()
                : schedule(((Number) d.get("work_schedule_id")).longValue());
        schedule.put("useDefault", d.get("work_schedule_id") == null);
        return map("id", id, "name", d.get("name"), "code", d.get("code"), "parentId", d.get("parent_id"), "path", path,
                "head", head(d.get("head_user_id"), false), "fallbackHead", d.get("head_user_id") == null ? fallback
                : null, "workSchedule", schedule, "directMemberCount", db
                .count("SELECT COUNT(*) FROM users WHERE department_id=:id", map("id", id)), "childCount", db
                .count("SELECT COUNT(*) FROM departments WHERE parent_id=:id", map("id", id)));
    }

    @Transactional
    public Map<String, Object> createDepartment(DepartmentCreate r) {
        lockTree();
        department(r.parentId());
        long id = db
                .insert(
                "INSERT INTO departments(name,code,parent_id,sort_order,active) VALUES(:name,:code,:parent,:sort,1)",
                map("name", r.name(), "code", UUID.randomUUID().toString(), "parent", r.parentId(), "sort", r
                .sortOrder()));
        db.update("UPDATE departments SET code=:code WHERE id=:id", map("id", id, "code", String.format("D-%04d", id)));
        return detail(id);
    }

    @Transactional
    public Map<String, Object> patchDepartment(long id, DepartmentPatch r) {
        lockTree();
        department(id);
        if (r.name() != null && r.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        db.update("UPDATE departments SET name=COALESCE(:name,name),sort_order=COALESCE(:sort,sort_order) WHERE id=:id",
                map("id", id, "name", r.name(), "sort", r.sortOrder()));
        return detail(id);
    }

    @Transactional
    public Map<String, Object> move(long id, Long parentId) {
        lockTree();
        var d = department(id);
        if (parentId == null || d.get("parent_id") == null || permissions.descendants(id).contains(parentId)) {
            throw new BusinessException(ErrorCode.INVALID_DEPARTMENT_MOVE);
        }
        department(parentId);
        db.update("UPDATE departments SET parent_id=:parent WHERE id=:id", map("id", id, "parent", parentId));
        return detail(id);
    }

    @Transactional
    public Map<String, Object> setHead(long id, Long userId) {
        lockTree();
        department(id);
        if (userId != null) {
            var user = db.one("SELECT id,department_id,status FROM users WHERE id=:id FOR UPDATE", map("id", userId),
                    ErrorCode.USER_NOT_FOUND);
            if (number(user, "department_id") != id) {
                throw new BusinessException(ErrorCode.HEAD_NOT_IN_DEPARTMENT);
            }
            if (!"ACTIVE".equals(user.get("status"))) {
                throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE, org.springframework.http.HttpStatus
                        .UNPROCESSABLE_ENTITY);
            }
        }
        db.update("UPDATE departments SET head_user_id=:head WHERE id=:id", map("id", id, "head", userId));
        return detail(id);
    }

    @Transactional
    public void deleteDepartment(long id) {
        lockTree();
        department(id);
        if (db.count("SELECT COUNT(*) FROM users WHERE department_id=:id", map("id", id)) > 0 || db
                .count("SELECT COUNT(*) FROM departments WHERE parent_id=:id", map("id", id)) > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_EMPTY);
        }
        db.update("DELETE FROM departments WHERE id=:id", map("id", id));
    }

    private Map<String, Object> schedule(long id) {
        return db.one(
                "SELECT id,name,TIME_FORMAT(start_time,'%H:%i') AS startTime,TIME_FORMAT(end_time,'%H:%i') AS endTime,late_grace_min AS lateGraceMin FROM work_schedules WHERE id=:id",
                map("id", id), ErrorCode.RESOURCE_NOT_FOUND);
    }

    public Map<String, Object> defaultSchedule() {
        return schedule(number(db.one("SELECT id FROM work_schedules WHERE is_default=1", Map.of(), ErrorCode
                .RESOURCE_NOT_FOUND), "id"));
    }

    private void validateSchedule(Schedule r) {
        if (r.startTime() == null || r.endTime() == null || !r.endTime().isAfter(r.startTime()) || r.lateGraceMin()
                == null || r.lateGraceMin() < 0 || r.lateGraceMin() > 60) {
            throw new BusinessException(ErrorCode.INVALID_WORK_SCHEDULE);
        }
    }

    @Transactional
    public Map<String, Object> defaultSchedule(Schedule r) {
        validateSchedule(r);
        db.update("UPDATE work_schedules SET start_time=:start,end_time=:end,late_grace_min=:grace WHERE is_default=1",
                map("start", r.startTime(), "end", r.endTime(), "grace", r.lateGraceMin()));
        return defaultSchedule();
    }

    @Transactional
    public Map<String, Object> departmentSchedule(long id, Schedule r) {
        lockTree();
        var d = department(id);
        if (Boolean.TRUE.equals(r.useDefault())) {
            db.update("UPDATE departments SET work_schedule_id=NULL WHERE id=:id", map("id", id));
        } else {
            validateSchedule(r);
            Long scheduleId = d.get("work_schedule_id") == null ? null : ((Number) d.get("work_schedule_id"))
                    .longValue();
            var params = map("start", r.startTime(), "end", r.endTime(), "grace", r.lateGraceMin(), "name", "부서 근무시간");
            if (scheduleId == null) {
                scheduleId = db.insert(
                        "INSERT INTO work_schedules(name,start_time,end_time,late_grace_min,is_default) VALUES(:name,:start,:end,:grace,0)",
                        params);
            } else {
                params.put("id", scheduleId);
                db
                        .update(
                        "UPDATE work_schedules SET start_time=:start,end_time=:end,late_grace_min=:grace WHERE id=:id",
                        params);
            }
            db.update("UPDATE departments SET work_schedule_id=:schedule WHERE id=:id",
                    map("id", id, "schedule", scheduleId));
        }
        return detail(id);
    }

    public List<Map<String, Object>> positions() {
        return db.rows("SELECT id,name,rank_order AS rankOrder FROM positions ORDER BY rank_order,id", Map.of());
    }

    @Transactional
    public Map<String, Object> createPosition(String name) {
        long id = db.insert("INSERT INTO positions(name,rank_order) VALUES(:name,0)", map("name", name));
        return map("id", id, "name", name, "rankOrder", 0);
    }

    @Transactional
    public Map<String, Object> renamePosition(long id, String name) {
        db.one("SELECT id FROM positions WHERE id=:id", map("id", id), ErrorCode.POSITION_NOT_FOUND);
        db.update("UPDATE positions SET name=:name WHERE id=:id", map("id", id, "name", name));
        return map("id", id, "name", name);
    }

    @Transactional
    public List<Map<String, Object>> orderPositions(List<PositionOrder> order) {
        Set<Long> ids = new HashSet<>();
        for (var item : order) {
            if (!ids.add(item.id())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (db.update("UPDATE positions SET rank_order=:rank WHERE id=:id",
                    map("rank", item.rankOrder(), "id", item.id())) == 0) {
                throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
            }
        }
        return positions();
    }

    @Transactional
    public void deletePosition(long id) {
        db.one("SELECT id FROM positions WHERE id=:id FOR UPDATE", map("id", id), ErrorCode.POSITION_NOT_FOUND);
        if (db.count("SELECT COUNT(*) FROM users WHERE position_id=:id", map("id", id)) > 0) {
            throw new BusinessException(ErrorCode.POSITION_IN_USE);
        }
        db.update("DELETE FROM positions WHERE id=:id", map("id", id));
    }

    public List<Map<String, Object>> holidays(int year) {
        return db.rows(
                "SELECT id,holiday_date AS date,name,substitute FROM holidays WHERE holiday_date>=:start AND holiday_date<:end ORDER BY holiday_date",
                map("start", LocalDate.of(year, 1, 1), "end", LocalDate.of(year + 1, 1, 1)));
    }

    @Transactional
    public Map<String, Object> createHoliday(HolidayCreate r) {
        if (db.count("SELECT COUNT(*) FROM holidays WHERE holiday_date=:date", map("date", r.date())) > 0) {
            throw new BusinessException(ErrorCode.HOLIDAY_ALREADY_EXISTS);
        }
        try {
            long id = db.insert("INSERT INTO holidays(holiday_date,name,substitute) VALUES(:date,:name,:sub)",
                    map("date", r.date(), "name", r.name(), "sub", r.substitute()));
            return map("id", id, "date", r.date(), "name", r.name(), "substitute", r.substitute());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.HOLIDAY_ALREADY_EXISTS);
        }
    }

    @Transactional
    public Map<String, Object> patchHoliday(long id, HolidayPatch r) {
        db.one("SELECT id FROM holidays WHERE id=:id", map("id", id), ErrorCode.RESOURCE_NOT_FOUND);
        db.update("UPDATE holidays SET name=COALESCE(:name,name),substitute=COALESCE(:sub,substitute) WHERE id=:id",
                map("id", id, "name", r.name(), "sub", r.substitute()));
        return db.one("SELECT id,holiday_date AS date,name,substitute FROM holidays WHERE id=:id", map("id", id),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Transactional
    public void deleteHoliday(long id) {
        if (db.update("DELETE FROM holidays WHERE id=:id", map("id", id)) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
