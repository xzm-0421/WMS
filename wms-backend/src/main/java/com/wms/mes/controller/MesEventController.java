package com.wms.mes.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.mes.entity.MesEventLog;
import com.wms.mes.service.MesEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统管理-数据埋点")
@RestController
@RequestMapping("/system/mes-events")
@RequiredArgsConstructor
public class MesEventController {

    private final MesEventService mesEventService;

    @Operation(summary = "埋点事件列表")
    @GetMapping
    public ApiResult<PageResult<MesEventLog>> list(
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) String bizNo,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(mesEventService.page(eventName, bizNo, current, size));
    }
}
