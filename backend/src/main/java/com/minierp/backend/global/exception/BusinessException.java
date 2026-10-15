package com.minierp.backend.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final org.springframework.http.HttpStatus status;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.status = errorCode.getStatus();
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
        this.status = errorCode.getStatus();
    }
    public BusinessException(ErrorCode errorCode,org.springframework.http.HttpStatus status) {
        super(errorCode.getMessage()); this.errorCode=errorCode; this.status=status;
    }
}
