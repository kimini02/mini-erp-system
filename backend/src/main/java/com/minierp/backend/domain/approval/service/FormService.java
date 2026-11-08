package com.minierp.backend.domain.approval.service;

import com.minierp.backend.domain.approval.repository.FormQueries;
import com.minierp.backend.domain.approval.dto.ApprovalFormResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalFormSummaryResponseDto;
import com.minierp.backend.domain.approval.repository.FormStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormService {
    private final FormQueries queries;
    private final FormStore store;
    private final ApprovalLineResolver resolver;
    public record Options(Boolean dedupeApprover, Boolean fallbackToUpper, Boolean skipSelf, Boolean allowLineEdit) {
    }

    public record Step(String lineKind, String approverType, String roleCode, Long userId) {
    }

    private record StepValues(Object form, String kind, String type, Long role, Long user, int order) {
    }

    public static String shortName(String code) {
        return switch (code) {
            case "LEAVE" -> "휴가";
            case "OVERTIME" -> "특근";
            case "GENERAL" -> "품의";
            case "ATTENDANCE_FIX" -> "근태 정정";
            default -> throw new BusinessException(ErrorCode.FORM_NOT_FOUND);
        };
    }

    private String description(String code) {
        return switch (code) {
            case "LEAVE" -> "연차·반차·병가·경조 휴가를 신청합니다.";
            case "OVERTIME" -> "하루 단위의 특근을 신청합니다.";
            case "ATTENDANCE_FIX" -> "지난 근무일의 출퇴근 기록 정정을 신청합니다.";
            default -> "업무에 필요한 일반 품의를 올립니다.";
        };
    }

    public List<ApprovalFormSummaryResponseDto> getForms() {
        return queries.findForms().stream().map(form -> {
            String code = (String) form.get("code");
            String description = description(code);
            return new ApprovalFormSummaryResponseDto(code, (String) form.get("name"), shortName(code),
                    (String) form.get("doc_prefix"), description, bool(form, "allow_line_edit"),
                    number(form, "approvers"), number(form, "refs"));
        }).toList();
    }

    public ApprovalFormResponseDto getForm(String code) {
        var form = resolver.getForm(code);
        String formCode = (String) form.get("code");
        String description = description(formCode);
        return new ApprovalFormResponseDto(formCode, (String) form.get("name"), shortName(formCode),
                (String) form.get("doc_prefix"), description, ApprovalFormResponseDto.Options.from(form),
                queries.findSteps(form.get("id")).stream()
                        .map(step -> ApprovalFormResponseDto.Step.from(step, stepLabel(step))).toList());
    }

    private String stepLabel(Map<String, Object> step) {
        return switch ((String) step.get("approver_type")) {
            case "DEPT_HEAD" -> "기안자의 부서장";
            case "UPPER_DEPT_HEAD" -> "기안자의 상위 부서장";
            case "ROLE" -> (String) step.get("role_name");
            default -> (String) step.get("user_name");
        };
    }

    @Transactional
    public ApprovalFormResponseDto updateFormOptions(String code, Options options) {
        var form = resolver.getForm(code);
        store.updateOptions(form.get("id"), options.dedupeApprover(), options.fallbackToUpper(),
                options.skipSelf(), options.allowLineEdit());
        return getForm(code);
    }

    @Transactional
    public ApprovalFormResponseDto updateFormSteps(String code, List<Step> steps) {
        var form = resolver.getForm(code);
        if (steps == null || steps.isEmpty()
                || steps.stream()
                        .anyMatch(step -> step == null || step.lineKind() == null
                                || !Set.of("APPROVER", "REFERENCE").contains(step.lineKind())
                                || step.approverType() == null
                                || !Set.of("DEPT_HEAD", "UPPER_DEPT_HEAD", "ROLE", "USER")
                                        .contains(step.approverType()))
                || steps.stream().noneMatch(step -> "APPROVER".equals(step.lineKind()))) {
            throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
        }
        List<StepValues> resolved = resolveStepValues(form, steps);
        store.lockForm(form.get("id"));
        store.deleteSteps(form.get("id"));
        for (var step : resolved) {
            store.insertStep(step.form(), step.kind(), step.type(), step.role(), step.user(), step.order());
        }
        return getForm(code);
    }
    private List<StepValues> resolveStepValues(Map<String, Object> form, List<Step> steps) {
        List<StepValues> resolved = new ArrayList<>();
        for (var step : steps) {
            Long role = null;
            Long user = null;
            if (step.approverType().equals("ROLE")) {
                if (step.roleCode() == null || step.roleCode().isBlank()) {
                    throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
                }
                role = number(queries.findRole(step.roleCode()), "id");
            }
            if (step.approverType().equals("USER")) {
                if (step.userId() == null) {
                    throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
                }
                user = number(queries.findUser(step.userId()), "id");
            }
            resolved.add(
                    new StepValues(form.get("id"), step.lineKind(), step.approverType(), role, user,
                            resolved.size() + 1));
        }
        return resolved;
    }
}
