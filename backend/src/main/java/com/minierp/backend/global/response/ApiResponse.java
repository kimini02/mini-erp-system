package com.minierp.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Clock;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String message;
    private final LocalDateTime timestamp;
    public static<T> ApiResponse<T> success(Clock clock, T data) {
        return new ApiResponse<>(true, data, "요청이 성공적으로 처리되었습니다.", LocalDateTime.now(clock));
    }

    public static<T> ApiResponse<T> success(Clock clock, T data, String message) {
        return new ApiResponse<>(true, data, message, LocalDateTime.now(clock));
    }

    public static ApiResponse<Void> successMessage(Clock clock, String message) {
        return new ApiResponse<>(true, null, message, LocalDateTime.now(clock));
    }
}
