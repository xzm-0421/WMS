package com.wms.system.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.system.entity.SysOperationLog;
import com.wms.system.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "系统管理-操作日志")
@RestController
@RequestMapping("/system/operation-logs")
@RequiredArgsConstructor
public class SysOperationLogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "操作日志列表")
    @GetMapping
    public ApiResult<PageResult<SysOperationLog>> list(
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String responseResult,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(operationLogService.page(
                operatorName, module, operationType, responseResult, start, end, current, size));
    }
}
