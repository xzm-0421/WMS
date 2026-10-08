package com.wms.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.auth.security.LoginUser;
import com.wms.common.security.SecurityUtils;
import com.wms.system.entity.SysOperationLog;
import com.wms.system.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 关键操作 AOP：写入 sys_operation_log（记录操作人、模块、类型、参数、结果、IP、设备、耗时）。
 * 记录失败不影响主流程；密码/token 等敏感参数脱敏。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final int MAX_PARAM_LEN = 2000;
    private static final String MASK = "******";
    /** 递归脱敏 JSON 中的密码/token/secret 字段值 */
    private static final Pattern SENSITIVE_JSON =
            Pattern.compile("(?i)\"([^\"]*(password|pwd|token|secret)[^\"]*)\"\\s*:\\s*\"[^\"]*\"");

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                saveLog(pjp, operationLog, error, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("写入操作日志失败: {}", e.getMessage());
            }
        }
    }

    private void saveLog(ProceedingJoinPoint pjp, OperationLog operationLog, Throwable error, long costMs) {
        SysOperationLog record = new SysOperationLog();
        record.setModule(operationLog.module());
        record.setOperationType(operationLog.type());
        record.setOperationTime(LocalDateTime.now());

        LoginUser user = currentUserQuietly();
        if (user != null) {
            record.setOperatorId(String.valueOf(user.getUserId()));
            record.setOperatorName(user.getRealName() != null ? user.getRealName() : user.getUsername());
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            record.setIpAddress(resolveIp(request));
            record.setDeviceInfo(truncate(nullSafe(request.getHeader("X-Device-No")) != null
                    ? request.getHeader("X-Device-No") + " | " + request.getHeader("User-Agent")
                    : request.getHeader("User-Agent"), 200));
        }

        record.setRequestParams(serializeParams(pjp));
        String content = operationLog.content();
        if (error != null) {
            record.setResponseResult("FAIL");
            record.setOperationContent(truncate((content + " 失败: " + error.getMessage()).trim(), 500));
        } else {
            record.setResponseResult("SUCCESS");
            record.setOperationContent(truncate(content, 500));
        }
        operationLogService.save(record);
    }

    private String serializeParams(ProceedingJoinPoint pjp) {
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            String[] names = signature.getParameterNames();
            Object[] args = pjp.getArgs();
            Map<String, Object> params = new LinkedHashMap<>();
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    Object value = args[i];
                    if (isIgnored(value)) {
                        continue;
                    }
                    String name = names[i];
                    params.put(name, isSensitive(name) ? MASK : value);
                }
            }
            String json = objectMapper.writeValueAsString(params);
            json = SENSITIVE_JSON.matcher(json).replaceAll("\"$1\":\"" + MASK + "\"");
            return truncate(json, MAX_PARAM_LEN);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isIgnored(Object value) {
        return value instanceof HttpServletRequest
                || value instanceof jakarta.servlet.http.HttpServletResponse
                || value instanceof MultipartFile
                || value instanceof MultipartFile[];
    }

    private static boolean isSensitive(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("token")
                || lower.contains("secret") || lower.contains("pwd");
    }

    private static LoginUser currentUserQuietly() {
        try {
            return SecurityUtils.currentUser();
        } catch (Exception e) {
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
