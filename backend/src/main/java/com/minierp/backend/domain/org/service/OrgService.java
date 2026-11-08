package com.minierp.backend.domain.org.service;

import com.minierp.backend.domain.org.dto.OrgRequests.*;
import com.minierp.backend.domain.org.dto.*;
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

    public OrgDepartmentTreeResponseDto tree() {
        // Exactly two queries regardless of department count, including heads/positions.
        var departments = queries.findDepartments();
        var counts = queries.findDepartmentMemberCounts();
        Map<Long, Long> members = new HashMap<>();
        for (var row : counts) {
            members.put(number(row, "department_id"), number(row, "members"));
        }
        Map<Long, List<Map<String, Object>>> children = new LinkedHashMap<>();
        Map<String, Object> root = null;
        for (var row : departments) {
            if (row.get("parent_id") == null) {
                root = row;
            } else {
                children.computeIfAbsent(number(row, "parent_id"), parent -> new ArrayList<>()).add(row);
            }
        }
        return root == null ? null : departmentTree(root, children, members);
    }

    private OrgDepartmentTreeResponseDto departmentTree(
            Map<String, Object> row,
            Map<Long, List<Map<String, Object>>> children,
            Map<Long, Long> members) {
        long id = number(row, "id");
        List<OrgDepartmentTreeResponseDto> descendants = new ArrayList<>();
        Long directMembers = members.getOrDefault(id, 0L);
        long total = directMembers;
        for (var child : children.getOrDefault(id, List.of())) {
            var descendant = departmentTree(child, children, members);
            descendants.add(descendant);
            total += descendant.totalMemberCount();
        }
        return new OrgDepartmentTreeResponseDto(id, (String) row.get("name"), (String) row.get("code"),
                OrgDepartmentResponseDto.Head.fromDepartment(row), directMembers, total, descendants);
    }

    private Map<String, Object> head(Object userId) {
        if (userId == null) {
            return null;
        }
        return queries.findHead(userId);
    }

    public OrgDepartmentResponseDto getDepartment(long id) {
        var departmentRow = department(id);
        List<OrgDepartmentResponseDto.Path> path = new ArrayList<>();
        OrgDepartmentResponseDto.FallbackHead fallback = null;
        Object parent = departmentRow.get("parent_id");
        while (parent != null) {
            var ancestor = department(((Number) parent).longValue());
            path.add(0, OrgDepartmentResponseDto.Path.from(ancestor));
            if (fallback == null && ancestor.get("head_user_id") != null) {
                fallback = OrgDepartmentResponseDto.FallbackHead.from(head(ancestor.get("head_user_id")));
            }
            parent = ancestor.get("parent_id");
        }
        var schedule = departmentRow.get("work_schedule_id") == null
                ? defaultScheduleRow()
                : schedule(((Number) departmentRow.get("work_schedule_id")).longValue());
        var workSchedule = OrgDepartmentResponseDto.WorkSchedule.from(schedule,
                departmentRow.get("work_schedule_id") == null);
        Long parentId = departmentRow.get("parent_id") == null
                ? null
                : ((Number) departmentRow.get("parent_id")).longValue();
        return new OrgDepartmentResponseDto(id, (String) departmentRow.get("name"), (String) departmentRow.get("code"),
                parentId, path, OrgDepartmentResponseDto.Head.from(head(departmentRow.get("head_user_id"))),
                departmentRow.get("head_user_id") == null ? fallback : null, workSchedule,
                queries.countDepartmentMembers(id), queries.countChildDepartments(id));
    }

    @Transactional
    public OrgDepartmentResponseDto createDepartment(DepartmentCreate request) {
        lockTree();
        department(request.parentId());
        long id = store.insertDepartment(request.name(), UUID.randomUUID().toString(), request.parentId(),
                request.sortOrder());
        store.updateDepartmentCode(id, String.format("D-%04d", id));
        return getDepartment(id);
    }

    @Transactional
    public OrgDepartmentResponseDto updateDepartment(long id, DepartmentPatch request) {
        lockTree();
        department(id);
        if (request.name() != null && request.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        store.updateDepartment(id, request.name(), request.sortOrder());
        return getDepartment(id);
    }

    @Transactional
    public OrgDepartmentResponseDto updateDepartmentParent(long id, Long parentId) {
        lockTree();
        var departmentRow = department(id);
        if (parentId == null || departmentRow.get("parent_id") == null
                || permissions.descendants(id).contains(parentId)) {
            throw new BusinessException(ErrorCode.INVALID_DEPARTMENT_MOVE);
        }
        department(parentId);
        store.updateDepartmentParent(id, parentId);
        return getDepartment(id);
    }

    @Transactional
    public OrgDepartmentResponseDto setHead(long id, Long userId) {
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
        return getDepartment(id);
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

    private Map<String, Object> defaultScheduleRow() {
        return schedule(number(queries.findDefaultSchedule(), "id"));
    }

    public OrgWorkScheduleResponseDto getDefaultWorkSchedule() {
        return OrgWorkScheduleResponseDto.from(defaultScheduleRow());
    }

    private void validateSchedule(Schedule request) {
        if (request.startTime() == null || request.endTime() == null || !request.endTime().isAfter(request.startTime())
                || request.lateGraceMin() == null || request.lateGraceMin() < 0 || request.lateGraceMin() > 60) {
            throw new BusinessException(ErrorCode.INVALID_WORK_SCHEDULE);
        }
    }

    @Transactional
    public OrgWorkScheduleResponseDto updateDefaultWorkSchedule(Schedule request) {
        validateSchedule(request);
        store.updateDefaultSchedule(request.startTime(), request.endTime(), request.lateGraceMin());
        return getDefaultWorkSchedule();
    }

    @Transactional
    public OrgDepartmentResponseDto updateDepartmentSchedule(long id, Schedule request) {
        lockTree();
        var departmentRow = department(id);
        if (Boolean.TRUE.equals(request.useDefault())) {
            store.updateDepartmentToDefaultSchedule(id);
        } else {
            validateSchedule(request);
            Long scheduleId = departmentRow.get("work_schedule_id") == null
                    ? null
                    : ((Number) departmentRow.get("work_schedule_id")).longValue();
            var start = request.startTime();
            var end = request.endTime();
            var grace = request.lateGraceMin();
            var name = "부서 근무시간";
            if (scheduleId == null) {
                scheduleId = store.insertWorkSchedule(start, end, grace, name);
            } else {
                store.updateWorkSchedule(start, end, grace, name, scheduleId);
            }
            store.updateDepartmentSchedule(id, scheduleId);
        }
        return getDepartment(id);
    }

    public List<OrgPositionResponseDto> getPositions() {
        return queries.findPositions().stream().map(OrgPositionResponseDto::from).toList();
    }

    @Transactional
    public OrgPositionCreateResponseDto createPosition(String name) {
        long id = store.insertPosition(name);
        return new OrgPositionCreateResponseDto(id, name, 0);
    }

    @Transactional
    public OrgPositionUpdateResponseDto updatePosition(long id, String name) {
        queries.findPosition(id);
        store.updatePositionName(id, name);
        return new OrgPositionUpdateResponseDto(id, name);
    }

    @Transactional
    public List<OrgPositionResponseDto> updatePositionOrder(List<PositionOrder> order) {
        Set<Long> ids = new HashSet<>();
        for (var item : order) {
            if (!ids.add(item.id())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (store.updatePositionOrder(item.rankOrder(), item.id()) == 0) {
                throw new BusinessException(ErrorCode.POSITION_NOT_FOUND);
            }
        }
        return getPositions();
    }

    @Transactional
    public void deletePosition(long id) {
        store.lockPosition(id);
        if (queries.countPositionMembers(id) > 0) {
            throw new BusinessException(ErrorCode.POSITION_IN_USE);
        }
        store.deletePosition(id);
    }

    public List<OrgHolidayResponseDto> getHolidays(int year) {
        return queries.findHolidays(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1)).stream()
                .map(OrgHolidayResponseDto::from)
                .toList();
    }

    @Transactional
    public OrgHolidayResponseDto createHoliday(HolidayCreate request) {
        if (queries.countHolidaysOnDate(request.date()) > 0) {
            throw new BusinessException(ErrorCode.HOLIDAY_ALREADY_EXISTS);
        }
        try {
            long id = store.insertHoliday(request.date(), request.name(), request.substitute());
            return new OrgHolidayResponseDto(id, request.date(), request.name(), request.substitute());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.HOLIDAY_ALREADY_EXISTS);
        }
    }

    @Transactional
    public OrgHolidayResponseDto updateHoliday(long id, HolidayPatch request) {
        queries.findHolidayIdentity(id);
        store.updateHoliday(id, request.name(), request.substitute());
        return OrgHolidayResponseDto.from(queries.findHoliday(id));
    }

    @Transactional
    public void deleteHoliday(long id) {
        if (store.deleteHoliday(id) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
