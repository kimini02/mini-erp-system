package com.minierp.backend.domain.task.controller;

import com.minierp.backend.domain.task.service.BoardTaskService;
import com.minierp.backend.domain.task.dto.BoardTaskRequests.*;
import com.minierp.backend.domain.task.dto.*;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.Clock;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@RequirePermission(menu = "PROJECT", action = Action.VIEW)
public class TaskBoardController {
    private final BoardTaskService service;
    private final Clock clock;
    @GetMapping("/projects/{id:[0-9]+}/board")
    public ApiResponse<TaskBoardResponseDto> getBoard(@PathVariable long id) {
        return ApiResponse.success(clock, service.getBoard(id));
    }

    @PostMapping("/projects/{id:[0-9]+}/tasks")
    public ResponseEntity<ApiResponse<TaskDetailResponseDto>> createTask(@PathVariable long id,
            @Valid @RequestBody Create body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(clock, service.createTask(id, body)));
    }

    @GetMapping("/tasks/{id:[0-9]+}")
    public ApiResponse<TaskDetailResponseDto> getTask(@PathVariable long id) {
        return ApiResponse.success(clock, service.getTask(id));
    }

    @PatchMapping("/tasks/{id:[0-9]+}")
    public ApiResponse<TaskDetailResponseDto> updateTask(@PathVariable long id, @Valid @RequestBody Patch body) {
        return ApiResponse.success(clock, service.updateTask(id, body));
    }

    @PostMapping("/tasks/{id:[0-9]+}/move")
    public ApiResponse<TaskMoveResponseDto> moveTask(@PathVariable long id, @Valid @RequestBody Move body) {
        return ApiResponse.success(clock, service.moveTask(id, body));
    }
}
