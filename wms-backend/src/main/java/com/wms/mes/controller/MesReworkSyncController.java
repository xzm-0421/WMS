package com.wms.mes.controller;

import com.wms.common.audit.OperationLog;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.mes.dto.MesDefectCreateRequest;
import com.wms.mes.dto.MesReworkSequenceVo;
import com.wms.mes.dto.MesSyncPanelVo;
import com.wms.mes.entity.MesDefect;
import com.wms.mes.entity.MesErpOutbox;
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
    @OperationLog(module = "轻MES", type = "CREATE", content = "发起返工")
    @PostMapping("/defects")
    public ApiResult<MesReworkSequenceVo> create(@RequestBody MesDefectCreateRequest request) {
        return ApiResult.ok("返工序列已创建", reworkService.create(request));
    }

    @Operation(summary = "返工序列")
    @GetMapping("/defects/{defectNo}")
    public ApiResult<MesReworkSequenceVo> sequence(@PathVariable String defectNo) {
        return ApiResult.ok(reworkService.sequence(defectNo));
    }

    @Operation(summary = "完成返工")
    @OperationLog(module = "轻MES", type = "COMPLETE", content = "完成返工")
    @PostMapping("/defects/{defectNo}/complete")
    public ApiResult<MesDefect> complete(@PathVariable String defectNo) {
        return ApiResult.ok("已标记返工完成", reworkService.complete(defectNo));
    }

    @Operation(summary = "标记二次返工")
    @OperationLog(module = "轻MES", type = "SECONDARY", content = "标记二次返工")
    @PostMapping("/defects/{defectNo}/secondary")
    public ApiResult<MesDefect> secondary(@PathVariable String defectNo) {
        return ApiResult.ok("已标记二次返工", reworkService.markSecondary(defectNo));
    }

    @Operation(summary = "关闭返工")
    @OperationLog(module = "轻MES", type = "CLOSE", content = "关闭返工")
    @PostMapping("/defects/{defectNo}/close")
    public ApiResult<MesDefect> close(@PathVariable String defectNo) {
        return ApiResult.ok("已关闭返工", reworkService.close(defectNo));
    }

    @Operation(summary = "同步状态面板")
    @GetMapping("/sync/panel")
    public ApiResult<MesSyncPanelVo> panel() {
        return ApiResult.ok(syncWorkerService.panel());
    }

    @Operation(summary = "不良/返工同步任务列表")
    @GetMapping("/sync/outbox")
    public ApiResult<PageResult<MesErpOutbox>> outbox(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(syncWorkerService.pageOutbox(bizType, bizNo, syncStatus, current, size));
    }

    @Operation(summary = "重试不良/返工同步任务")
    @OperationLog(module = "轻MES", type = "RETRY", content = "不良/返工同步重试")
    @PostMapping("/sync/outbox/{id}/retry")
    public ApiResult<Void> retryOutbox(@PathVariable Long id) {
        syncWorkerService.retryOutbox(id);
        return ApiResult.ok("已加入重试队列", null);
    }
}
