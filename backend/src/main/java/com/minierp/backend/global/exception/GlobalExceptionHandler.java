package com.minierp.backend.global.exception;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import com.minierp.backend.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(exception.getStatus())
                .body(ErrorResponse.of(clock, errorCode, exception.getMessage(), exception.getDetails()));
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class,
            IllegalArgumentException.class })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(clock, errorCode, extractValidationMessage(exception)));
    }

    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class })
    public ResponseEntity<ErrorResponse> handleInvalidInput() {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(clock, errorCode));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType() {
        ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(clock, errorCode));
    }

    @ExceptionHandler({ org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class })
    public ResponseEntity<ErrorResponse> handleNotFound(Exception exception) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ErrorResponse.of(clock, ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleRemovedBoardMethod(
            org.springframework.web.HttpRequestMethodNotSupportedException exception,
            jakarta.servlet.http.HttpServletRequest request) {
        // These v1 methods were removed in M4; their URLs still serve different v2 methods.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean removed = (request.getMethod().equals("PUT") && path.matches("/api/v1/(projects|tasks)/[0-9]+"))
                || ((request.getMethod().equals("GET") || request.getMethod().equals("POST"))
                        && path.matches("/api/v1/projects/[0-9]+/members"));
        if (removed) {
            return handleNotFound(exception);
        }
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(clock, errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(clock, errorCode));
    }

    private String extractValidationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            FieldError fieldError = methodArgumentNotValidException.getBindingResult().getFieldError();
            return fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.INVALID_INPUT_VALUE.getMessage();
        }
        if (exception instanceof BindException bindException) {
            FieldError fieldError = bindException.getBindingResult().getFieldError();
            return fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.INVALID_INPUT_VALUE.getMessage();
        }
        return exception.getMessage() == null ? ErrorCode.INVALID_INPUT_VALUE.getMessage() : exception.getMessage();
    }
}
