package com.minierp.backend.domain.task.controller;

import java.time.Clock;

import com.minierp.backend.domain.task.dto.TaskAssignmentRequestDto;
import com.minierp.backend.domain.task.dto.TaskAssignmentResponseDto;
import com.minierp.backend.domain.task.dto.TaskCreateRequestDto;
import com.minierp.backend.domain.task.dto.RecentAssignmentDto;
import com.minierp.backend.domain.task.dto.TaskResponseDto;
import com.minierp.backend.domain.task.dto.TaskStatusUpdateDto;
import com.minierp.backend.domain.task.dto.TaskUpdateRequestDto;
import com.minierp.backend.domain.task.service.TaskService;
import com.minierp.backend.domain.user.entity.UserRole;
import com.minierp.backend.global.exception.BusinessException;
import com.minierp.backend.global.exception.ErrorCode;
import com.minierp.backend.global.response.ApiResponse;
import com.minierp.backend.global.security.CurrentUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 업무(Task) REST 컨트롤러
 * - 비즈니스 로직 없이 Service에 위임만 수행
 * - @Valid로 요청 DTO 유효성 검증, ResponseEntity<ApiResponse<T>>로 응답 통일
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final Clock clock;

    private final TaskService taskService;
    private final com.minierp.backend.domain.task.service.BoardTaskService board;
    private final CurrentUserResolver currentUserResolver;

    @com.minierp.backend.global.permission.RequirePermission(menu="PROJECT",action=com.minierp.backend.global.permission.Action.VIEW)
    @PostMapping
    public ResponseEntity<?> createTask(
            @Valid @RequestBody TaskCreateRequestDto request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(clock,board.create(request.getProjectId(),new com.minierp.backend.domain.task.dto.BoardTaskRequests.Create(request.getTaskTitle(),request.getTaskContent(),request.getTaskStatus(),request.getAssigneeIds(),request.getEndDate(),request.getPriority()))));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> getTasks(Authentication authentication) {
        List<TaskResponseDto> response = taskService.getTasks(
                currentUserResolver.resolveUserId(authentication),
                currentUserResolver.resolveUserRole(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(clock, response, "업무 목록 조회가 완료되었습니다."));
    }



    @com.minierp.backend.global.permission.RequirePermission(menu="PROJECT",action=com.minierp.backend.global.permission.Action.VIEW)
    @PutMapping("/{taskId}")
    public ResponseEntity<?> updateTask(
            @PathVariable Long taskId,
            @Valid @RequestBody TaskUpdateRequestDto request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(clock,board.patch(taskId,new com.minierp.backend.domain.task.dto.BoardTaskRequests.Patch(request.getTaskTitle(),request.getTaskContent(),request.getEndDate(),request.getPriority(),null))));
    }

    @com.minierp.backend.global.permission.RequirePermission(menu="PROJECT",action=com.minierp.backend.global.permission.Action.VIEW)
    @PatchMapping("/{taskId}/status")
    public ResponseEntity<?> changeTaskStatus(
            @PathVariable Long taskId,
            @Valid @RequestBody TaskStatusUpdateDto request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(clock,board.move(taskId,new com.minierp.backend.domain.task.dto.BoardTaskRequests.Move(request.getTaskStatus(),null,request.getVersion()))));
    }

    @com.minierp.backend.global.permission.RequirePermission(menu="PROJECT",action=com.minierp.backend.global.permission.Action.VIEW)
    @PostMapping("/{taskId}/assignments")
    public ResponseEntity<?> addAssignment(
            @PathVariable Long taskId,
            @Valid @RequestBody TaskAssignmentRequestDto request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(clock,board.changeAssignment(taskId,request.getUserId(),true)));
    }

    @GetMapping("/{taskId}/assignments")
    public ResponseEntity<ApiResponse<List<TaskAssignmentResponseDto>>> getAssignments(
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        List<TaskAssignmentResponseDto> response = taskService.getAssignments(
                taskId,
                currentUserResolver.resolveUserId(authentication),
                currentUserResolver.resolveUserRole(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(clock, response, "업무 담당자 목록 조회가 완료되었습니다."));
    }

    @com.minierp.backend.global.permission.RequirePermission(menu="PROJECT",action=com.minierp.backend.global.permission.Action.VIEW)
    @DeleteMapping("/{taskId}/assignments/{userId}")
    public ResponseEntity<?> removeAssignment(
            @PathVariable Long taskId,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        board.changeAssignment(taskId,userId,false);
        return ResponseEntity.ok(ApiResponse.successMessage(clock,"업무 담당자 배정이 해제되었습니다."));
    }

    @GetMapping("/recent-assignments")
    public ResponseEntity<ApiResponse<List<RecentAssignmentDto>>> getRecentAssignments(Authentication authentication) {
        List<RecentAssignmentDto> response = taskService.getRecentAssignments(
                currentUserResolver.resolveUserId(authentication),
                currentUserResolver.resolveUserRole(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(clock, response, "최근 업무 배정 이력 조회가 완료되었습니다."));
    }
}
