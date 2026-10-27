package com.minierp.backend.domain.task.controller;
import com.minierp.backend.domain.task.service.BoardTaskService;
import com.minierp.backend.domain.task.dto.BoardTaskRequests.*;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.Clock;
@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor @RequirePermission(menu="PROJECT",action=Action.VIEW)
public class TaskBoardController {
 private final BoardTaskService service;private final Clock clock;
 @GetMapping("/projects/{id}/board") public ApiResponse<?> board(@PathVariable long id){return ApiResponse.success(clock,service.board(id));}
 @PostMapping("/projects/{id}/tasks") public ResponseEntity<?> create(@PathVariable long id,@Valid @RequestBody Create body){return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(clock,service.create(id,body)));}
 @GetMapping("/tasks/{id}") public ApiResponse<?> detail(@PathVariable long id){return ApiResponse.success(clock,service.detail(id));}
 @PatchMapping("/tasks/{id}") public ApiResponse<?> patch(@PathVariable long id,@Valid @RequestBody Patch body){return ApiResponse.success(clock,service.patch(id,body));}
 @PostMapping("/tasks/{id}/move") public ApiResponse<?> move(@PathVariable long id,@Valid @RequestBody Move body){return ApiResponse.success(clock,service.move(id,body));}
}
