package com.minierp.backend.domain.user.service;

import com.minierp.backend.domain.user.dto.EmployeeRequests.*;
import com.minierp.backend.domain.user.dto.RegistrationResult;
import com.minierp.backend.domain.user.repository.EmployeeRepository;
import com.minierp.backend.domain.user.repository.EmployeeQueries;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.domain.user.repository.EmployeeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {
    private final com.minierp.backend.domain.org.repository.DepartmentTreeLock treeLock;
    private final EmployeeQueries queries;
    private final EmployeeStore store;
    private final EmployeeRepository repository;
    private final PermissionAccess access;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final com.minierp.backend.global.service.InvitationTokenStore invitationTokens;
    public record InvitationEmailChanged(long userId, String oldEmail) {
    }

    public record InvitationRequested(long userId, String email, RegistrationResult result) {
    }

    public Map<String, Object> raw(long id) {
        return queries.findUser(id);
    }

    public void lockChief() {
        store.lockChief();
    }

    public void protectLastChief(long id) {
        var chiefs = store.lockActiveChiefs();
        if (chiefs.size() <= 1 && chiefs.stream().anyMatch(r -> number(r, "id") == id)) {
            throw new BusinessException(ErrorCode.LAST_CHIEF);
        }
    }

    private void notHead(long id) {
        if (!store.lockHeadDepartments(id).isEmpty()) {
            throw new BusinessException(ErrorCode.HEAD_MUST_BE_REASSIGNED);
        }
    }

    private void requireRoleUpdateForPrivilegedTarget(long id) {
        if (queries.countPrivilegedRoles(id) > 0) {
            access.require("ROLE", Action.UPDATE);
        }
    }

    public List<Map<String, Object>> views(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var rows = queries.findEmployeeViews(ids);
        Map<Long, List<Map<String, Object>>> roles = new HashMap<>();
        for (var row : queries.findEmployeeRoles(ids)) {
            roles.computeIfAbsent(number(row, "user_id"), k -> new ArrayList<>())
                    .add(map("code", row.get("code"), "name", row.get("name")));
        }
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (var row : rows) {
            long id = number(row, "id");
            var userRoles = roles.getOrDefault(id, List.of());
            String legacy = userRoles.stream().anyMatch(r -> "CHIEF".equals(r.get("code")))
                    ? "ADMIN"
                    : userRoles.stream().anyMatch(r -> "LEADER".equals(r.get("code"))) ? "TEAM_LEADER" : "USER";
            String code = (String) row.get("department_code");
            code = switch (code) {
                case "D-0002" -> "01";
                case "D-0003" -> "02";
                case "D-0004" -> "03";
                default -> code;
            };
            // M1 프론트 작업 후 제거: departmentCode, departmentName, position, role aliases.
            byId.put(id,
                    map("id", id, "loginId", row.get("login_id"), "name", row.get("user_name"),
                            "email", row.get("user_email"),
                            "department", map("id", row.get("department_id"), "name", row.get("department_name")),
                            "positionId", row.get("position_id"), "position", row.get("position_name"),
                            "departmentCode", code, "departmentName", row.get("department_name"), "role", legacy,
                            "roles", userRoles, "hireDate", row.get("hire_date"),
                            "retiredDate", row.get("retired_date"), "status", row.get("status"),
                            "invitationPending", row.get("user_pw") == null,
                            "remainingAnnualLeave", row.get("remaining_annual_leave"),
                            "createdAt", row.get("created_at"), "updatedAt", row.get("updated_at")));
        }
        return ids.stream().map(byId::get).toList();
    }

    public static Map<String, Object> page(List<?> content, int page, int size, long total) {
        long pages = (total + size - 1) / size;
        return map("content", content,
                "page", map("number", page, "size", size, "totalElements", total, "totalPages", pages,
                        "first", page == 0, "last", page + 1 >= pages, "numberOfElements", content.size()));
    }

    public Map<String, Object> list(
            String q,
            Long departmentId,
            boolean includeSub,
            String status,
            int page,
            int size,
            String sort) {
        if (page < 0 || size < 1 || size > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (status != null) {
            try {
                com.minierp.backend.domain.user.entity.UserStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        var scope = access.scope("EMPLOYEE");
        List<Long> departments = null;
        if (departmentId != null) {
            if (!scope.includesDepartment(departmentId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            queries.findDepartment(departmentId);
            departments = includeSub ? access.descendants(departmentId) : List.of(departmentId);
        }
        String[] parts = sort.split(",");
        var columns = queries.findSortColumns();
        if (!columns.containsKey(parts[0]) || parts.length > 2
                || parts.length == 2 && !Set.of("asc", "desc").contains(parts[1].toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String order = queries.findSortOrder(columns, parts);
        var selected = repository.search(scope, q, departments, status, page, size, order);
        var result = page(views(selected.ids()), page, size, selected.total());
        result.put("statusCounts", selected.counts());
        return result;
    }

    public Map<String, Object> detail(long id) {
        if (id != access.actor()) {
            access.require("EMPLOYEE", Action.VIEW);
            access.assertUserInScope("EMPLOYEE", id);
        }
        raw(id);
        return views(List.of(id)).get(0);
    }

    public String preview(LocalDate hireDate) {
        var rows = queries.findLoginSequence(hireDate.getYear());
        long next = rows.isEmpty() ? 1 : number(rows.get(0), "last_seq") + 1;
        return String.format("%d%03d", hireDate.getYear(), next);
    }

    private List<Long> roleIds(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new BusinessException(ErrorCode.ROLE_REQUIRED);
        }
        Set<String> unique = new HashSet<>(codes);
        var rows = queries.findRolesByCodes(unique);
        if (rows.size() != unique.size()) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        return rows.stream().map(r -> number(r, "id")).toList();
    }

    @Transactional
    public RegistrationResult register(Create r) {
        // Creating an account must not bypass the separate permission to assign roles.
        if (r.roleCodes() != null && r.roleCodes().stream().anyMatch(code -> !"EMPLOYEE".equals(code))) {
            access.require("ROLE", Action.UPDATE);
        }
        var roles = roleIds(r.roleCodes());
        queries.findDepartment(r.departmentId());
        var position = queries.findPositionName(r.positionId());
        if (queries.countEmail(r.email()) > 0) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        int year = r.hireDate().getYear();
        store.insertLoginSequence(year);
        long next = number(store.lockLoginSequence(year), "last_seq") + 1;
        store.updateLoginSequence(year, next);
        String loginId = String.format("%d%03d", year, next);
        long id;
        try {
            id = store.insertEmployee(loginId, r.name(), r.email(), r.departmentId(), r.positionId(),
                    r.hireDate(), LocalDateTime.now(clock));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        for (long role : roles) {
            store.insertUserRole(id, role);
        }
        var result = new RegistrationResult(id, loginId);
        events.publishEvent(new InvitationRequested(id, r.email(), result));
        return result;
    }

    @Transactional
    public Map<String, Object> patch(long id, Patch r) {
        // Same order as OrgService.setHead: tree first, then user/current head reads.
        if (r.departmentId() != null) {
            treeLock.lock();
        }
        access.assertUserInScope("EMPLOYEE", id);
        var old = r.email() == null && r.departmentId() == null
                ? raw(id)
                : store.lockUser(id);
        if (r.email() != null && !r.email().equals(old.get("user_email"))) {
            requireRoleUpdateForPrivilegedTarget(id);
        }
        if (r.departmentId() != null) {
            if (number(old, "department_id") != r.departmentId()) {
                notHead(id);
            }
            queries.findDepartment(r.departmentId());
        }
        if (r.positionId() != null) {
            queries.findPosition(r.positionId());
        }
        if (r.name() != null && r.name().isBlank() || r.email() != null && r.email().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        try {
            store.updateEmployee(id, r.name(), r.email(), r.departmentId(), r.positionId(), r.hireDate(),
                    LocalDateTime.now(clock));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (r.email() != null && !r.email().equals(old.get("user_email"))) {
            events.publishEvent(new InvitationEmailChanged(id, (String) old.get("user_email")));
        }
        return views(List.of(id)).get(0);
    }

    @Transactional
    public Map<String, Object> status(long id, com.minierp.backend.domain.user.entity.UserStatus status) {
        if (status != com.minierp.backend.domain.user.entity.UserStatus.ACTIVE) {
            treeLock.lock();
        }
        access.assertUserInScope("EMPLOYEE", id);
        lockChief();
        var user = store.lockUser(id);
        if (!status.name().equals(user.get("status"))) {
            if (status != com.minierp.backend.domain.user.entity.UserStatus.ACTIVE) {
                notHead(id);
                protectLastChief(id);
            }
            store.updateStatus(id, status.name(), LocalDate.now(clock),
                    status == com.minierp.backend.domain.user.entity.UserStatus.ACTIVE ? 0 : 1,
                    LocalDateTime.now(clock));
        }
        return views(List.of(id)).get(0);
    }

    @Transactional
    public void replaceRoles(long id, List<String> codes) {
        lockChief();
        raw(id);
        var ids = roleIds(codes);
        if (!codes.contains("CHIEF")) {
            protectLastChief(id);
        }
        store.deleteUserRoles(id);
        for (long role : ids) {
            store.insertAssignedRole(id, role);
        }
    }

    @Transactional
    public RegistrationResult invitation(long id) {
        var user = store.lockUser(id);
        requireRoleUpdateForPrivilegedTarget(id);
        if (user.get("user_pw") != null) {
            throw new BusinessException(ErrorCode.INVITATION_ALREADY_ACCEPTED);
        }
        invitationTokens.revoke(id);
        var result = new RegistrationResult(id, (String) user.get("login_id"));
        events.publishEvent(new InvitationRequested(id, (String) user.get("user_email"), result));
        return result;
    }
}
