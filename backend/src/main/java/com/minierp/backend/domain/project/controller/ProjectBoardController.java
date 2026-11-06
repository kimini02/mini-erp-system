package com.minierp.backend.domain.project.controller;

import com.minierp.backend.domain.project.service.BoardProjectService;
import com.minierp.backend.domain.project.dto.BoardProjectRequests.*;
import com.minierp.backend.domain.project.entity.ProjectStatus;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
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
    public ApiResponse<?> list(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(clock, service.list(status, q, page, size));
    }

    @PostMapping
    @RequirePermission(menu = "PROJECT", action = Action.VIEW)
    public ResponseEntity<?> create(@Valid @RequestBody Create body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(clock, service.create(body)));
    }

    @GetMapping("/{id:[0-9]+}")
    @RequirePermission(menu = "PROJECT", action = Action.VIEW)
    public ApiResponse<?> detail(@PathVariable long id) {
        return ApiResponse.success(clock, service.detail(id));
    }

    // U is checked in the service together with the project-owner exception.
    @PatchMapping("/{id:[0-9]+}")
    @RequirePermission(menu = "PROJECT", action = Action.VIEW)
    public ApiResponse<?> patch(@PathVariable long id, @Valid @RequestBody Patch body) {
        return ApiResponse.success(clock, service.patch(id, body));
    }

    @PutMapping("/{id:[0-9]+}/members")
    @RequirePermission(menu = "PROJECT", action = Action.VIEW)
    public ApiResponse<?> members(@PathVariable long id, @Valid @RequestBody Members body) {
        return ApiResponse.success(clock, service.replaceMembers(id, body.userIds()));
    }
}
