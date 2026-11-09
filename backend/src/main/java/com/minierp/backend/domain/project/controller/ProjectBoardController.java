package com.minierp.backend.domain.project.controller;

import com.minierp.backend.domain.project.service.BoardProjectService;
import com.minierp.backend.domain.project.dto.BoardProjectRequests.*;
import com.minierp.backend.domain.project.entity.ProjectStatus;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import com.minierp.backend.global.response.PageResponseDto;
import com.minierp.backend.domain.project.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.Clock;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectBoardController {
    private final BoardProjectService service;
    private final Clock clock;
    @GetMapping
    @RequirePermission(menu = "PROJECT", action = Action.VIEW)
    public ApiResponse<PageResponseDto<ProjectSummaryResponseDto>> getProjects(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(clock, service.getProjects(status, query, page, size));
    }

    @PostMapping
    @RequirePermission(menu = "PROJECT", action = Action.VIEW)
    public ResponseEntity<ApiResponse<ProjectDetailResponseDto>> createProject(@Valid @RequestBody Create body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(clock, service.createProject(body)));
    }

    @GetMapping("/{id:[0-9]+}")
    @RequirePermission(menu = "PROJECT", action = Action.VIEW)
    public ApiResponse<ProjectDetailResponseDto> getProject(@PathVariable long id) {
        return ApiResponse.success(clock, service.getProject(id));
    }

    // U is checked in the service together with the project-owner exception.
    @PatchMapping("/{id:[0-9]+}")
    @RequirePermission(menu = "PROJECT", action = Action.VIEW)
    public ApiResponse<ProjectDetailResponseDto> updateProject(@PathVariable long id, @Valid @RequestBody Patch body) {
        return ApiResponse.success(clock, service.updateProject(id, body));
    }

    @PutMapping("/{id:[0-9]+}/members")
    @RequirePermission(menu = "PROJECT", action = Action.VIEW)
    public ApiResponse<ProjectDetailResponseDto> updateProjectMembers(@PathVariable long id,
            @Valid @RequestBody Members body) {
        return ApiResponse.success(clock, service.updateProjectMembers(id, body.userIds()));
    }
}
