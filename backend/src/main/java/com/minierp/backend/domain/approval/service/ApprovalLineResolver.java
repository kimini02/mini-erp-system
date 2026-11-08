package com.minierp.backend.domain.approval.service;

import com.minierp.backend.domain.approval.dto.DocumentRequest.ManualStep;
import com.minierp.backend.domain.approval.dto.ApprovalLinePreviewResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalPersonResponseDto;
import com.minierp.backend.domain.approval.repository.ApprovalLineQueries;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
public class ApprovalLineResolver {
    private final ApprovalLineQueries queries;
    public Map<String, Object> getForm(String code) {
        return queries.findForm(code);
    }

    public Map<String, Object> getPerson(long id) {
        return queries.findPerson(id);
    }

    public record Resolved(
            int order,
            String kind,
            Map<String, Object> person,
            String source,
            String rule,
            String skipReason,
            boolean fallback,
            String note) {
    }

    public List<Resolved> resolveLine(
            Map<String, Object> form,
            long drafter,
            long draftDepartment,
            List<ManualStep> manual) {
        if (manual != null && !bool(form, "allow_line_edit")) {
            throw new BusinessException(ErrorCode.LINE_EDIT_NOT_ALLOWED);
        }
        List<Map<String, Object>> steps = resolveSteps(form, manual);
        List<Resolved> resolved = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (var step : steps) {
            String type = (String) step.get("approver_type");
            String kind = (String) step.get("line_kind");
            try {
                Map<String, Object> person;
                boolean fallback = false;
                String note = null;
                switch (type) {
                    case "USER" -> person = getPerson(number(step, "user_id"));
                    case "ROLE" -> {
                        var rows = queries.findRoleHolder(step.get("role_id"));
                        if (rows.isEmpty()) {
                            throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND);
                        }
                        person = getPerson(number(rows.get(0), "id"));
                    }
                    case "DEPT_HEAD", "UPPER_DEPT_HEAD" -> {
                        Long department = draftDepartment;
                        if (type.equals("UPPER_DEPT_HEAD")) {
                            department = parent(department);
                        }
                        String sourceDepartment = department == null
                                ? null
                                : (String) queries.findDepartmentName(department).get("name");
                        person = null;
                        Set<Long> visited = new HashSet<>();
                        while (department != null && visited.add(department)) {
                            var departmentRow = queries.findDepartmentHead(department);
                            if (departmentRow.get("head_user_id") != null) {
                                var candidate = getPerson(number(departmentRow, "head_user_id"));
                                if ("ACTIVE".equals(candidate.get("status"))) {
                                    person = candidate;
                                    break;
                                }
                            }
                            if (!bool(form, "fallback_to_upper")) {
                                break;
                            }
                            department = departmentRow.get("parent_id") == null
                                    ? null
                                    : number(departmentRow, "parent_id");
                            fallback = true;
                        }
                        if (person == null) {
                            throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND);
                        }
                        if (fallback) {
                            note = sourceDepartment + " 부서장이 비어 있어 " + person.get("department") + " 부서장에게 넘어갑니다.";
                        }
                    }
                    default -> throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
                }
                if (!"ACTIVE".equals(person.get("status"))) {
                    throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND);
                }
                long approver = number(person, "id");
                String skip = null;
                if (kind.equals("APPROVER")) {
                    if (bool(form, "skip_self") && approver == drafter) {
                        skip = "SELF";
                    } else if (bool(form, "dedupe_approver") && seen.contains(approver)) {
                        skip = "DUPLICATE";
                    }
                    seen.add(approver);
                } else if (approver == drafter) {
                    skip = "SELF";
                }
                String label = manual != null ? "직접 지정" : switch (type) {
                    case "DEPT_HEAD" -> "기안자의 부서장";
                    case "UPPER_DEPT_HEAD" -> "기안자의 상위 부서장";
                    case "ROLE" -> "역할 담당자";
                    default -> "지정 사원";
                };
                resolved.add(
                        new Resolved(((Number) step.get("step_order")).intValue(), kind, person,
                                manual == null ? "RULE" : "MANUAL", label, skip, fallback, note));
            } catch (BusinessException ex) {
                if (ex.getErrorCode() != ErrorCode.APPROVER_NOT_FOUND) {
                    throw ex;
                }
                if (kind.equals("REFERENCE")) {
                    resolved.add(
                            new Resolved(((Number) step.get("step_order")).intValue(), kind, null,
                                    manual == null ? "RULE" : "MANUAL", label(type), "NO_REFERENCE", false,
                                    "참조할 사람이 없어 건너뜁니다."));
                    continue;
                }
                throw new ResolutionFailure(resolved, ((Number) step.get("step_order")).intValue(), type, kind);
            }
        }
        return resolved;
    }

    private List<Map<String, Object>> resolveSteps(Map<String, Object> form, List<ManualStep> manual) {
        List<Map<String, Object>> steps;
        if (manual != null) {
            if (manual.stream()
                    .anyMatch(manualStep -> manualStep == null || manualStep.userId() == null
                            || manualStep.lineKind() == null
                            || !Set.of("APPROVER", "REFERENCE").contains(manualStep.lineKind()))
                    || manual.stream().noneMatch(manualStep -> "APPROVER".equals(manualStep.lineKind()))) {
                throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
            }
            steps = new ArrayList<>();
            int stepOrder = 0;
            for (var manualStep : manual) {
                steps.add(manualStepRow(++stepOrder, manualStep));
            }
        } else {
            steps = queries.findSteps(form.get("id"));
        }
        if (steps.stream().noneMatch(manualStep -> "APPROVER".equals(manualStep.get("line_kind")))) {
            throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
        }
        return steps;
    }

    private Map<String, Object> manualStepRow(int order, ManualStep step) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("step_order", order);
        row.put("line_kind", step.lineKind());
        row.put("approver_type", "USER");
        row.put("user_id", step.userId());
        return row;
    }

    private Long parent(long department) {
        var row = queries.findParent(department);
        return row.get("parent_id") == null ? null : number(row, "parent_id");
    }

    public ApprovalLinePreviewResponseDto getLinePreview(String code, long drafter, List<ManualStep> manual) {
        var form = getForm(code);
        var user = getPerson(drafter);
        try {
            var resolved = resolveLine(form, drafter, number(user, "department_id"), manual);
            return new ApprovalLinePreviewResponseDto(resolved.stream().map(this::previewRow).toList(),
                    resolved.stream()
                            .filter(resolvedStep -> resolvedStep.kind().equals("APPROVER")
                                    && resolvedStep.skipReason() == null)
                            .map(resolvedStep -> ApprovalPersonResponseDto.from(resolvedStep.person()))
                            .toList(), references(resolved), null);
        } catch (ResolutionFailure ex) {
            var rows = new ArrayList<>(ex.resolved.stream().map(this::previewRow).toList());
            String label = switch (ex.rule) {
                case "DEPT_HEAD" -> "기안자의 부서장";
                case "UPPER_DEPT_HEAD" -> "기안자의 상위 부서장";
                case "ROLE" -> "역할 담당자";
                default -> "지정 사원";
            };
            rows.add(new ApprovalLinePreviewResponseDto.Row(ex.order, ex.kind, label, null, "ERROR", ex.getMessage()));
            return new ApprovalLinePreviewResponseDto(rows, List.of(), references(ex.resolved), ex.getMessage());
        }
    }

    private static String label(String type) {
        return switch (type) {
            case "DEPT_HEAD" -> "기안자의 부서장";
            case "UPPER_DEPT_HEAD" -> "기안자의 상위 부서장";
            case "ROLE" -> "역할 담당자";
            default -> "지정 사원";
        };
    }

    private List<ApprovalPersonResponseDto> references(List<Resolved> resolved) {
        return resolved.stream()
                .filter(resolvedStep -> resolvedStep.kind().equals("REFERENCE") && resolvedStep.skipReason() == null)
                .map(resolvedStep -> ApprovalPersonResponseDto.from(resolvedStep.person()))
                .toList();
    }

    private ApprovalLinePreviewResponseDto.Row previewRow(Resolved resolvedStep) {
        String note = resolvedStep.note();
        if ("SELF".equals(resolvedStep.skipReason())) {
            note = "기안자 본인이라 이 단계는 건너뜁니다.";
        }
        if ("DUPLICATE".equals(resolvedStep.skipReason())) {
            note = "앞 단계와 같은 결재자라 건너뜁니다.";
        }
        return new ApprovalLinePreviewResponseDto.Row(resolvedStep.order(), resolvedStep.kind(), resolvedStep.rule(),
                resolvedStep.person() == null ? null : ApprovalPersonResponseDto.from(resolvedStep.person()),
                resolvedStep.skipReason() != null ? "SKIPPED" : resolvedStep.fallback() ? "FALLBACK" : "OK", note);
    }

    private static final class ResolutionFailure extends BusinessException {
        final List<Resolved> resolved;
        final int order;
        final String rule;
        final String kind;
        ResolutionFailure(List<Resolved> resolved, int order, String rule, String kind) {
            super(ErrorCode.APPROVER_NOT_FOUND);
            this.resolved = List.copyOf(resolved);
            this.order = order;
            this.rule = rule;
            this.kind = kind;
        }
    }

}
