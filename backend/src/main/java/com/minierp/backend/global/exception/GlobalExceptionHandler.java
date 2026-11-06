package com.minierp.backend.global.exception;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import com.minierp.backend.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
/**
* 전역 예외 처리기 — BusinessException + ErrorCode 조합으로 일관된 응답 포맷 제공
*/
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {
    private final Clock clock;
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(e.getStatus())
                .body(ErrorResponse.of(clock, errorCode, e.getMessage(), e.getDetails()));
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class, IllegalArgumentException.class })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(clock, errorCode, extractValidationMessage(e)));
    }

    @ExceptionHandler({ org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class })
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ErrorResponse.of(clock, ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleRemovedBoardMethod(
            org.springframework.web.HttpRequestMethodNotSupportedException e,
            jakarta.servlet.http.HttpServletRequest request) {
        // These v1 methods were removed in M4; their URLs still serve different v2 methods.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean removed = (request.getMethod().equals("PUT") && path.matches("/api/v1/(projects|tasks)/[0-9]+"))
                || ((request.getMethod().equals("GET") || request.getMethod().equals("POST"))
                        && path.matches("/api/v1/projects/[0-9]+/members"));
        if (removed) {
            return handleNotFound(e);
        }
        // Preserve unrelated error behavior; general method-status cleanup is outside M4.
        return handleException(e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(clock, errorCode));
    }

    private String extractValidationMessage(Exception e) {
        if (e instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            FieldError fieldError = methodArgumentNotValidException.getBindingResult().getFieldError();
            return fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.INVALID_INPUT_VALUE.getMessage();
        }
        if (e instanceof BindException bindException) {
            FieldError fieldError = bindException.getBindingResult().getFieldError();
            return fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.INVALID_INPUT_VALUE.getMessage();
        }
        return e.getMessage() == null ? ErrorCode.INVALID_INPUT_VALUE.getMessage() : e.getMessage();
    }
}
