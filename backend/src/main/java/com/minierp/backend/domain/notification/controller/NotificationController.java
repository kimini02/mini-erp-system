package com.minierp.backend.domain.notification.controller;
import com.minierp.backend.domain.notification.service.NotificationQueryService;
import com.minierp.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
@RestController @RequestMapping("/api/v1/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final NotificationQueryService service;private final Clock clock;
    @GetMapping public ApiResponse<?> list(@RequestParam(defaultValue="false") boolean unreadOnly,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.success(clock,service.list(unreadOnly,page,size));}
    @GetMapping("/unread-count") public ApiResponse<?> count() {return ApiResponse.success(clock,service.unreadCount());}
    @PatchMapping("/{id}/read") public ApiResponse<?> read(@PathVariable long id) {service.read(id);return ApiResponse.success(clock,null);}
    @PostMapping("/read-all") public ApiResponse<?> readAll() {service.readAll();return ApiResponse.success(clock,null);}
}
