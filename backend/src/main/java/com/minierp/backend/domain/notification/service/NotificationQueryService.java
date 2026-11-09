package com.minierp.backend.domain.notification.service;

import com.minierp.backend.domain.notification.dto.*;
import com.minierp.backend.global.response.PageResponseDto;
import com.minierp.backend.domain.notification.repository.NotificationQueries;
import com.minierp.backend.domain.notification.repository.NotificationStore;
import com.minierp.backend.global.permission.PermissionAccess;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {
    private final NotificationQueries queries;
    private final NotificationStore store;
    private final PermissionAccess access;
    private final Clock clock;
    public PageResponseDto<NotificationItemResponseDto> getNotifications(boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        long user = access.actor();
        long offset = (long) page * size;
        long total = queries.countNotifications(user, size, offset, unreadOnly);
        var rows = queries.findNotifications(user, size, offset, unreadOnly)
                .stream()
                .map(NotificationItemResponseDto::from)
                .toList();
        return PageResponseDto.of(rows, page, size, total);
    }

    public NotificationUnreadCountResponseDto getUnreadCount() {
        return new NotificationUnreadCountResponseDto(queries.countUnread(access.actor()));
    }

    @Transactional
    public void markAsRead(long id) {
        var notification = queries.findOwner(id);
        if (number(notification, "user_id") != access.actor()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        store.updateRead(id, access.actor(), LocalDateTime.now(clock));
    }

    @Transactional
    public void markAllAsRead() {
        store.updateAllRead(access.actor(), LocalDateTime.now(clock));
    }
}
