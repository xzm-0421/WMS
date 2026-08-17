package com.wms.common.exception;

import com.wms.common.constant.ErrorCode;
import com.wms.common.result.ApiResult;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Map<String, Object>> handleBusiness(BusinessException e) {
        Map<String, Object> data = new HashMap<>();
        if (e.getErrorType() != null) {
            data.put("errorType", e.getErrorType());
        }
        if (e.getDetails() != null) {
            data.put("details", e.getDetails());
        }
        return ApiResult.fail(e.getCode(), e.getMessage(), data.isEmpty() ? null : data);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ApiResult<Void> handleBadCredentials(BadCredentialsException e) {
        return ApiResult.fail(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
    }

    @ExceptionHandler(io.jsonwebtoken.ExpiredJwtException.class)
    public ApiResult<Void> handleExpiredJwt(io.jsonwebtoken.ExpiredJwtException e) {
        return ApiResult.fail(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
    }

    @ExceptionHandler(io.jsonwebtoken.JwtException.class)
    public ApiResult<Void> handleJwt(io.jsonwebtoken.JwtException e) {
        return ApiResult.fail(ErrorCode.UNAUTHORIZED, "Token无效");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResult<Void> handleAccessDenied(AccessDeniedException e) {
        return ApiResult.fail(ErrorCode.FORBIDDEN, "无操作权限");
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResult<Map<String, Object>> handleValidation(Exception e) {
        String message = "请求参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().hasErrors()) {
            message = ex.getBindingResult().getFieldErrors().stream()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        Map<String, Object> data = Map.of("errorType", "VALIDATION_ERROR");
        return ApiResult.fail(ErrorCode.BAD_REQUEST, message, data);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiResult<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        String message = "数据保存失败，请检查必填字段是否完整";
        String raw = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        if (raw != null && raw.contains("NULL")) {
            message = "导入或提交的数据不完整，请使用系统模板并填写所有必填列";
        } else if (raw != null && raw.contains("uk_warehouse_code")) {
            message = "仓库编码已存在，请更换编码";
        } else if (raw != null && raw.contains("uk_location_code")) {
            message = "库位编码已存在，请更换编码";
        } else if (raw != null && raw.contains("uk_material_code")) {
            message = "物料编码已存在，请更换编码";
        } else if (raw != null && (raw.contains("UNIQUE KEY") || raw.contains("唯一"))) {
            message = "数据编码重复，请检查后重试";
        }
        return ApiResult.fail(ErrorCode.BAD_REQUEST, message, Map.of("errorType", "DATA_INTEGRITY"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResult<Map<String, Object>> handleNotFound(NoResourceFoundException e) {
        return ApiResult.fail(ErrorCode.NOT_FOUND, "接口不存在: " + e.getResourcePath(),
                Map.of("errorType", "NOT_FOUND"));
    }

    /**
     * 客户端主动断开（超时、切页、网络中断）时写响应失败，不属服务端业务故障。
     */
    @ExceptionHandler({
            ClientAbortException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleClientAbort(Exception e) {
        log.warn("Client aborted request: {}", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Map<String, Object>> handleException(Exception e) {
        if (isClientAbort(e)) {
            log.warn("Client aborted request: {}", rootMessage(e));
            return null;
        }
        log.error("Internal error", e);
        return ApiResult.fail(ErrorCode.INTERNAL_ERROR, "服务器内部错误",
                Map.of("errorType", "INTERNAL_ERROR"));
    }

    private static boolean isClientAbort(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof ClientAbortException || cur instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String msg = cur.getMessage();
            if (cur instanceof IOException && msg != null
                    && (msg.contains("中止了一个已建立的连接")
                    || msg.contains("Broken pipe")
                    || msg.contains("Connection reset")
                    || msg.contains("断开的管道"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : e.getClass().getSimpleName();
    }
}
