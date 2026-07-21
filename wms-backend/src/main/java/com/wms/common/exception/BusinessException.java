package com.wms.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String errorType;
    private final Object details;

    public BusinessException(int code, String message) {
        this(code, message, null, null);
    }

    public BusinessException(int code, String message, String errorType) {
        this(code, message, errorType, null);
    }

    public BusinessException(int code, String message, String errorType, Object details) {
        super(message);
        this.code = code;
        this.errorType = errorType;
        this.details = details;
    }
}
