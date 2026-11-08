package com.minierp.backend.domain.user.service;

import com.minierp.backend.domain.user.dto.EmployeeRequests.*;
import com.minierp.backend.domain.user.dto.RegistrationResult;
import com.minierp.backend.domain.user.dto.EmployeeDetailResponseDto;
import com.minierp.backend.domain.user.dto.EmployeeListResponseDto;
import com.minierp.backend.global.response.PageResponseDto;
import java.math.BigDecimal;
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
        if (chiefs.size() <= 1 && chiefs.stream().anyMatch(row -> number(row, "id") == id)) {
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

    public List<EmployeeDetailResponseDto> getEmployeeViews(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var rows = queries.findEmployeeViews(ids);
        Map<Long, List<EmployeeDetailResponseDto.Role>> roles = new HashMap<>();
        for (var row : queries.findEmployeeRoles(ids)) {
            roles.computeIfAbsent(number(row, "user_id"), userId -> new ArrayList<>())
                    .add(EmployeeDetailResponseDto.Role.from(row));
        }
        Map<Long, EmployeeDetailResponseDto> byId = new HashMap<>();
        for (var row : rows) {
            long id = number(row, "id");
            var userRoles = roles.getOrDefault(id, List.of());
            byId.put(id, employeeView(row, id, userRoles));
        }
        return ids.stream().map(byId::get).toList();
    }

    private EmployeeDetailResponseDto employeeView(
            Map<String, Object> row,
            long id,
            List<EmployeeDetailResponseDto.Role> userRoles) {
        String legacy = legacyRole(userRoles);
        String code = legacyDepartmentCode((String) row.get("department_code"));
        // M1 프론트 작업 후 제거: departmentCode, departmentName, position, role aliases.
        return new EmployeeDetailResponseDto(id, (String) row.get("login_id"), (String) row.get("user_name"),
                (String) row.get("user_email"), EmployeeDetailResponseDto.Department.from(row),
                number(row, "position_id"), (String) row.get("position_name"), code,
                (String) row.get("department_name"), legacy, userRoles, (LocalDate) row.get("hire_date"),
                (LocalDate) row.get("retired_date"), (String) row.get("status"), row.get("user_pw") == null,
                (BigDecimal) row.get("remaining_annual_leave"), (LocalDateTime) row.get("created_at"),
                (LocalDateTime) row.get("updated_at"));
    }

    private String legacyRole(List<EmployeeDetailResponseDto.Role> roles) {
        return roles.stream().anyMatch(role -> "CHIEF".equals(role.code()))
                ? "ADMIN"
                : roles.stream().anyMatch(role -> "LEADER".equals(role.code())) ? "TEAM_LEADER" : "USER";
    }

    private String legacyDepartmentCode(String code) {
        return switch (code) {
            case "D-0002" -> "01";
            case "D-0003" -> "02";
            case "D-0004" -> "03";
            default -> code;
        };
    }

    public static Map<String, Object> page(List<?> content, int page, int size, long total) {
        long pages = (total + size - 1) / size;
        return map("content", content,
                "page", map("number", page, "size", size, "totalElements", total, "totalPages", pages,
                        "first", page == 0, "last", page + 1 >= pages, "numberOfElements", content.size()));
    }

    public EmployeeListResponseDto getEmployees(
            String query,
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
        var columns = queries.sortColumns();
        if (!columns.containsKey(parts[0]) || parts.length > 2
                || parts.length == 2 && !Set.of("asc", "desc").contains(parts[1].toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String order = queries.orderBy(columns, parts);
        var selected = repository.search(scope, query, departments, status, page, size, order);
        var content = getEmployeeViews(selected.ids());
        var pagination = PageResponseDto.Page.of(page, size, selected.total(), content.size());
        return new EmployeeListResponseDto(content, pagination, selected.counts());
    }

    public EmployeeDetailResponseDto getEmployee(long id) {
        if (id != access.actor()) {
            access.require("EMPLOYEE", Action.VIEW);
            access.assertUserInScope("EMPLOYEE", id);
        }
        raw(id);
        return getEmployeeViews(List.of(id)).get(0);
    }

    public String getLoginIdPreview(LocalDate hireDate) {
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
        return rows.stream().map(row -> number(row, "id")).toList();
    }

    @Transactional
    public RegistrationResult register(Create request) {
        // Creating an account must not bypass the separate permission to assign roles.
        if (request.roleCodes() != null && request.roleCodes().stream().anyMatch(code -> !"EMPLOYEE".equals(code))) {
            access.require("ROLE", Action.UPDATE);
        }
        var roles = roleIds(request.roleCodes());
        queries.findDepartment(request.departmentId());
        var position = queries.findPositionName(request.positionId());
        if (queries.countEmail(request.email()) > 0) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        int year = request.hireDate().getYear();
        store.insertLoginSequence(year);
        long next = number(store.lockLoginSequence(year), "last_seq") + 1;
        store.updateLoginSequence(year, next);
        String loginId = String.format("%d%03d", year, next);
        long id;
        try {
            id = store.insertEmployee(loginId, request.name(), request.email(), request.departmentId(),
                    request.positionId(), request.hireDate(), LocalDateTime.now(clock));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        for (long role : roles) {
            store.insertUserRole(id, role);
        }
        var result = new RegistrationResult(id, loginId);
        events.publishEvent(new InvitationRequested(id, request.email(), result));
        return result;
    }

    @Transactional
    public EmployeeDetailResponseDto patch(long id, Patch request) {
        // Same order as OrgService.setHead: tree first, then user/current head reads.
        if (request.departmentId() != null) {
            treeLock.lock();
        }
        access.assertUserInScope("EMPLOYEE", id);
        var old = request.email() == null && request.departmentId() == null ? raw(id) : store.lockUser(id);
        if (request.email() != null && !request.email().equals(old.get("user_email"))) {
            requireRoleUpdateForPrivilegedTarget(id);
        }
        if (request.departmentId() != null) {
            if (number(old, "department_id") != request.departmentId()) {
                notHead(id);
            }
            queries.findDepartment(request.departmentId());
        }
        if (request.positionId() != null) {
            queries.findPosition(request.positionId());
        }
        if (request.name() != null && request.name().isBlank()
                || request.email() != null && request.email().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        try {
            store.updateEmployee(id, request.name(), request.email(), request.departmentId(),
                    request.positionId(), request.hireDate(), LocalDateTime.now(clock));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (request.email() != null && !request.email().equals(old.get("user_email"))) {
            events.publishEvent(new InvitationEmailChanged(id, (String) old.get("user_email")));
        }
        return getEmployeeViews(List.of(id)).get(0);
    }

    @Transactional
    public EmployeeDetailResponseDto status(long id, com.minierp.backend.domain.user.entity.UserStatus status) {
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
        return getEmployeeViews(List.of(id)).get(0);
    }

    @Transactional
    public void updateEmployeeRoles(long id, List<String> codes) {
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
