package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.mes.dto.MesCancelRequest;
import com.wms.mes.dto.MesDefectCreateRequest;
import com.wms.mes.dto.MesReportContextVo;
import com.wms.mes.dto.MesReportSubmitRequest;
import com.wms.mes.dto.MesReworkSequenceVo;
import com.wms.mes.dto.MesSyncPanelVo;
import com.wms.mes.dto.MesTransferSubmitRequest;
import com.wms.mes.entity.MesDefect;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesTransfer;
import com.wms.mobile.dto.MobileMesSyncRequest;
import com.wms.mobile.service.MobileMesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "移动端-轻MES现场作业")
@RestController
@RequestMapping("/mobile/mes")
@RequiredArgsConstructor
public class MobileMesController {

    private final MobileMesService mobileMesService;

    @Operation(summary = "报工上下文")
    @GetMapping("/reports/context")
    public ApiResult<MesReportContextVo> context(@RequestParam String moNo) {
        return ApiResult.ok(mobileMesService.context(moNo));
    }

    @Operation(summary = "提交报工")
    @PostMapping("/reports")
    public ApiResult<MesReport> submit(@RequestBody MesReportSubmitRequest request) {
        return ApiResult.ok("报工成功", mobileMesService.submitReport(request));
    }

    @Operation(summary = "我的报工列表")
    @GetMapping("/reports")
    public ApiResult<PageResult<MesReport>> reports(
            @RequestParam(required = false) String reportNo,
            @RequestParam(required = false) String moNo,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(mobileMesService.pageReports(reportNo, moNo, syncStatus, current, size));
    }

    @Operation(summary = "报工详情")
    @GetMapping("/reports/{reportNo}")
    public ApiResult<MesReport> reportDetail(@PathVariable String reportNo) {
        return ApiResult.ok(mobileMesService.reportDetail(reportNo));
    }

    @Operation(summary = "报工重试同步")
    @PostMapping("/reports/{reportNo}/retry")
    public ApiResult<Void> retryReport(@PathVariable String reportNo) {
        mobileMesService.retryReport(reportNo);
        return ApiResult.ok("已加入重试队列", null);
    }

    @Operation(summary = "取消报工暂存")
    @PostMapping("/reports/{reportNo}/cancel")
    public ApiResult<Void> cancelReport(@PathVariable String reportNo, @RequestBody MesCancelRequest request) {
        mobileMesService.cancelReport(reportNo, request);
        return ApiResult.ok("已取消暂存", null);
    }

    @Operation(summary = "离线报工批量补传")
    @PostMapping("/reports/sync")
    public ApiResult<Map<String, Object>> syncReports(@RequestBody MobileMesSyncRequest request) {
        return ApiResult.ok("同步完成", mobileMesService.syncReports(request));
    }

    @Operation(summary = "同步状态面板")
    @GetMapping("/sync/panel")
    public ApiResult<MesSyncPanelVo> panel() {
        return ApiResult.ok(mobileMesService.panel());
    }

    @Operation(summary = "提交工序转移")
    @PostMapping("/transfers")
    public ApiResult<MesTransfer> submitTransfer(@RequestBody MesTransferSubmitRequest request) {
        return ApiResult.ok("转移成功", mobileMesService.submitTransfer(request));
    }

    @Operation(summary = "转移单列表")
    @GetMapping("/transfers")
    public ApiResult<PageResult<MesTransfer>> transfers(
            @RequestParam(required = false) String transferNo,
            @RequestParam(required = false) String moNo,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(mobileMesService.pageTransfers(transferNo, moNo, syncStatus, current, size));
    }

    @Operation(summary = "转移单重试")
    @PostMapping("/transfers/{transferNo}/retry")
    public ApiResult<Void> retryTransfer(@PathVariable String transferNo) {
        mobileMesService.retryTransfer(transferNo);
        return ApiResult.ok("已加入重试队列", null);
    }

    @Operation(summary = "发起返工")
    @PostMapping("/defects")
    public ApiResult<MesReworkSequenceVo> createDefect(@RequestBody MesDefectCreateRequest request) {
        return ApiResult.ok("返工序列已创建", mobileMesService.createDefect(request));
    }

    @Operation(summary = "不良单列表")
    @GetMapping("/defects")
    public ApiResult<PageResult<MesDefect>> defects(
            @RequestParam(required = false) String moNo,
            @RequestParam(required = false) String reworkStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(mobileMesService.pageDefects(moNo, reworkStatus, current, size));
    }

    @Operation(summary = "返工序列")
    @GetMapping("/defects/{defectNo}")
    public ApiResult<MesReworkSequenceVo> defectSequence(@PathVariable String defectNo) {
        return ApiResult.ok(mobileMesService.defectSequence(defectNo));
    }
}
