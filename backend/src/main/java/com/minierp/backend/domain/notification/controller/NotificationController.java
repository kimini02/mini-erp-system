package com.minierp.backend.domain.notification.controller;

import com.minierp.backend.domain.notification.service.NotificationQueryService;
import com.minierp.backend.global.response.ApiResponse;
import com.minierp.backend.global.response.PageResponseDto;
import com.minierp.backend.domain.notification.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationQueryService service;
    private final Clock clock;
    @GetMapping
    public ApiResponse<PageResponseDto<NotificationItemResponseDto>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(clock, service.getNotifications(unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponseDto> getUnreadCount() {
        return ApiResponse.success(clock, service.getUnreadCount());
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable long id) {
        service.markAsRead(id);
        return ApiResponse.success(clock, null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        service.markAllAsRead();
        return ApiResponse.success(clock, null);
    }
}
