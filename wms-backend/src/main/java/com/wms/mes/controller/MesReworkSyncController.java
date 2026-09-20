package com.wms.mes.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.mes.dto.MesDefectCreateRequest;
import com.wms.mes.dto.MesReworkSequenceVo;
import com.wms.mes.dto.MesSyncPanelVo;
import com.wms.mes.entity.MesDefect;
import com.wms.mes.service.MesReworkService;
import com.wms.mes.service.MesSyncWorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "轻MES-返工与同步中心")
@RestController
@RequestMapping("/mes")
@RequiredArgsConstructor
public class MesReworkSyncController {

    private final MesReworkService reworkService;
    private final MesSyncWorkerService syncWorkerService;

    @Operation(summary = "不良单列表")
    @GetMapping("/defects")
    public ApiResult<PageResult<MesDefect>> defects(
            @RequestParam(required = false) String moNo,
            @RequestParam(required = false) String reworkStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(reworkService.page(moNo, reworkStatus, current, size));
    }

    @Operation(summary = "发起返工")
    @PostMapping("/defects")
    public ApiResult<MesReworkSequenceVo> create(@RequestBody MesDefectCreateRequest request) {
        return ApiResult.ok("返工序列已创建", reworkService.create(request));
    }

    @Operation(summary = "返工序列")
    @GetMapping("/defects/{defectNo}")
    public ApiResult<MesReworkSequenceVo> sequence(@PathVariable String defectNo) {
        return ApiResult.ok(reworkService.sequence(defectNo));
    }

    @Operation(summary = "同步状态面板")
    @GetMapping("/sync/panel")
    public ApiResult<MesSyncPanelVo> panel() {
        return ApiResult.ok(syncWorkerService.panel());
    }
}
