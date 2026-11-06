package com.minierp.backend.domain.org.service;

import com.minierp.backend.domain.org.dto.OrgRequests.*;
import com.minierp.backend.domain.org.repository.OrgQueries;
import com.minierp.backend.global.permission.PermissionAccess;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.domain.org.repository.OrgStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgService {
    private final OrgQueries queries;
    private final OrgStore store;
    private final com.minierp.backend.domain.org.repository.DepartmentTreeLock treeLock;
    private final PermissionAccess permissions;
    private Map<String, Object> department(long id) {
        return queries.findDepartment(id);
    }

    private void lockTree() {
        treeLock.lock();
    }

    public Map<String, Object> tree() {
        // Exactly two queries regardless of department count, including heads/positions.
        var departments = queries.findDepartments();
        var counts = queries.findDepartmentMemberCounts();
        Map<Long, Long> members = new HashMap<>();
        for (var row : counts) {
            members.put(number(row, "department_id"), number(row, "members"));
        }
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (var row : departments) {
            long id = number(row, "id");
            Object head = row.get("head_user_id") == null
                    ? null
                    : map("id", row.get("head_user_id"), "name", row.get("head_name"),
                            "position", row.get("head_position"));
            nodes.put(id,
                    map("id", id, "name", row.get("name"), "code", row.get("code"), "head", head,
                            "directMemberCount", members.getOrDefault(id, 0L), "totalMemberCount", 0L,
                            "children", new ArrayList<Map<String, Object>>()));
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
        var h = queries.findHead(userId);
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
        var schedule = d.get("work_schedule_id") == null
                ? defaultSchedule()
                : schedule(((Number) d.get("work_schedule_id")).longValue());
        schedule.put("useDefault", d.get("work_schedule_id") == null);
        return map("id", id, "name", d.get("name"), "code", d.get("code"), "parentId", d.get("parent_id"), "path", path,
                "head", head(d.get("head_user_id"), false),
                "fallbackHead", d.get("head_user_id") == null ? fallback : null, "workSchedule", schedule,
                "directMemberCount", queries.countDepartmentMembers(id),
                "childCount", queries.countChildDepartments(id));
    }

    @Transactional
    public Map<String, Object> createDepartment(DepartmentCreate r) {
        lockTree();
        department(r.parentId());
        long id = store.insertDepartment(r.name(), UUID.randomUUID().toString(), r.parentId(), r.sortOrder());
        store.updateDepartmentCode(id, String.format("D-%04d", id));
        return detail(id);
    }

    @Transactional
    public Map<String, Object> patchDepartment(long id, DepartmentPatch r) {
        lockTree();
        department(id);
        if (r.name() != null && r.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        store.updateDepartment(id, r.name(), r.sortOrder());
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
        store.updateDepartmentParent(id, parentId);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> setHead(long id, Long userId) {
        lockTree();
        department(id);
        if (userId != null) {
            var user = store.lockHeadCandidate(userId);
            if (number(user, "department_id") != id) {
                throw new BusinessException(ErrorCode.HEAD_NOT_IN_DEPARTMENT);
            }
            if (!"ACTIVE".equals(user.get("status"))) {
                throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE,
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        store.updateDepartmentHead(id, userId);
        return detail(id);
    }

    @Transactional
    public void deleteDepartment(long id) {
        lockTree();
        department(id);
        if (queries.countDepartmentMembers(id) > 0 || queries.countChildDepartments(id) > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_EMPTY);
        }
        store.deleteDepartment(id);
    }

    private Map<String, Object> schedule(long id) {
        return queries.findSchedule(id);
    }

    public Map<String, Object> defaultSchedule() {
        return schedule(number(queries.findDefaultSchedule(), "id"));
    }

    private void validateSchedule(Schedule r) {
        if (r.startTime() == null || r.endTime() == null || !r.endTime().isAfter(r.startTime())
                || r.lateGraceMin() == null || r.lateGraceMin() < 0 || r.lateGraceMin() > 60) {
            throw new BusinessException(ErrorCode.INVALID_WORK_SCHEDULE);
        }
    }

    @Transactional
    public Map<String, Object> defaultSchedule(Schedule r) {
        validateSchedule(r);
        store.updateDefaultSchedule(r.startTime(), r.endTime(), r.lateGraceMin());
        return defaultSchedule();
    }

    @Transactional
    public Map<String, Object> departmentSchedule(long id, Schedule r) {
        lockTree();
        var d = department(id);
        if (Boolean.TRUE.equals(r.useDefault())) {
            store.updateDepartmentToDefaultSchedule(id);
        } else {
            validateSchedule(r);
            Long scheduleId = d.get("work_schedule_id") == null
                    ? null
                    : ((Number) d.get("work_schedule_id")).longValue();
            var start = r.startTime();
            var end = r.endTime();
            var grace = r.lateGraceMin();
            var name = "부서 근무시간";
            if (scheduleId == null) {
                scheduleId = store.insertWorkSchedule(start, end, grace, name);
            } else {
                store.updateWorkSchedule(start, end, grace, name, scheduleId);
            }
            store.updateDepartmentSchedule(id, scheduleId);
        }
        return detail(id);
    }

    public List<Map<String, Object>> positions() {
        return queries.findPositions();
    }

    @Transactional
    public Map<String, Object> createPosition(String name) {
        long id = store.insertPosition(name);
        return map("id", id, "name", name, "rankOrder", 0);
    }

    @Transactional
    public Map<String, Object> renamePosition(long id, String name) {
        queries.findPosition(id);
        store.updatePositionName(id, name);
        return map("id", id, "name", name);
    }

    @Transactional
    public List<Map<String, Object>> orderPositions(List<PositionOrder> order) {
        Set<Long> ids = new HashSet<>();
        for (var item : order) {
            if (!ids.add(item.id())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (store.updatePositionOrder(item.rankOrder(), item.id()) == 0) {
                throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
            }
        }
        return positions();
    }

    @Transactional
    public void deletePosition(long id) {
        store.lockPosition(id);
        if (queries.countPositionMembers(id) > 0) {
            throw new BusinessException(ErrorCode.POSITION_IN_USE);
        }
        store.deletePosition(id);
    }

    public List<Map<String, Object>> holidays(int year) {
        return queries.findHolidays(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1));
    }

    @Transactional
    public Map<String, Object> createHoliday(HolidayCreate r) {
        if (queries.countHolidaysOnDate(r.date()) > 0) {
            throw new BusinessException(ErrorCode.HOLIDAY_ALREADY_EXISTS);
        }
        try {
            long id = store.insertHoliday(r.date(), r.name(), r.substitute());
            return map("id", id, "date", r.date(), "name", r.name(), "substitute", r.substitute());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.HOLIDAY_ALREADY_EXISTS);
        }
    }

    @Transactional
    public Map<String, Object> patchHoliday(long id, HolidayPatch r) {
        queries.findHolidayIdentity(id);
        store.updateHoliday(id, r.name(), r.substitute());
        return queries.findHoliday(id);
    }

    @Transactional
    public void deleteHoliday(long id) {
        if (store.deleteHoliday(id) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
