package com.minierp.backend.global.response;

import com.minierp.backend.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Clock;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse {
    private final boolean success;
    private final ErrorBody error;
    private final LocalDateTime timestamp;
    public static ErrorResponse of(Clock clock, ErrorCode errorCode) {
        return new ErrorResponse(false, new ErrorBody(errorCode.name(), errorCode.getMessage(), null), LocalDateTime
                .now(clock));
    }

    public static ErrorResponse of(Clock clock, ErrorCode errorCode, String message) {
        return new ErrorResponse(false, new ErrorBody(errorCode.name(), message, null), LocalDateTime.now(clock));
    }

    public static ErrorResponse of(Clock clock, ErrorCode code, String message, java.util.Map<String, Object> details) {
        return new ErrorResponse(false, new ErrorBody(code.name(), message, details), LocalDateTime.now(clock));
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ErrorBody {
        private final String code;
        private final String message;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private final java.util.Map<String, Object> details;
    }
}
