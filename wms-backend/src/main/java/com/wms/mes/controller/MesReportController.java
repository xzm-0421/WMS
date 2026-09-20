package com.wms.mes.controller;

import com.wms.common.excel.ExcelHttpHelper;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.mes.dto.MesCancelRequest;
import com.wms.mes.dto.MesReportContextVo;
import com.wms.mes.dto.MesReportSubmitRequest;
import com.wms.mes.dto.MesTransferSubmitRequest;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesTransfer;
import com.wms.mes.service.MesReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Tag(name = "轻MES-报工与转移")
@RestController
@RequestMapping("/mes")
@RequiredArgsConstructor
public class MesReportController {

    private final MesReportService reportService;

    @Operation(summary = "报工上下文（工单可执行工序）")
    @GetMapping("/reports/context")
    public ApiResult<MesReportContextVo> context(@RequestParam String moNo) {
        return ApiResult.ok(reportService.context(moNo));
    }

    @Operation(summary = "提交报工")
    @PostMapping("/reports")
    public ApiResult<MesReport> submit(@RequestBody MesReportSubmitRequest request) {
        return ApiResult.ok("报工成功", reportService.submit(request));
    }

    @Operation(summary = "报工记录列表")
    @GetMapping("/reports")
    public ApiResult<PageResult<MesReport>> list(
            @RequestParam(required = false) String reportNo,
            @RequestParam(required = false) String moNo,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        boolean all = reportService.canViewAllReports();
        return ApiResult.ok(reportService.page(reportNo, moNo, syncStatus, true, !all, current, size));
    }

    @Operation(summary = "报工记录详情")
    @GetMapping("/reports/{reportNo}")
    public ApiResult<MesReport> detail(@PathVariable String reportNo) {
        return ApiResult.ok(reportService.getByNo(reportNo));
    }

    @Operation(summary = "取消暂存")
    @PostMapping("/reports/{reportNo}/cancel")
    public ApiResult<Void> cancel(@PathVariable String reportNo, @RequestBody MesCancelRequest request) {
        reportService.cancelReport(reportNo, request);
        return ApiResult.ok("已取消暂存", null);
    }

    @Operation(summary = "手动重试同步")
    @PostMapping("/reports/{reportNo}/retry")
    public ApiResult<Void> retry(@PathVariable String reportNo) {
        reportService.retryReport(reportNo);
        return ApiResult.ok("已加入重试队列", null);
    }

    @Operation(summary = "导出报工记录")
    @GetMapping("/reports/export")
    public void export(
            @RequestParam(required = false) String reportNo,
            @RequestParam(required = false) String moNo,
            @RequestParam(required = false) String syncStatus,
            HttpServletResponse response) throws IOException {
        boolean all = reportService.canViewAllReports();
        ExcelHttpHelper.writeXlsx(response, "mes-reports.xlsx",
                out -> reportService.exportReports(reportNo, moNo, syncStatus, !all, out));
    }

    @Operation(summary = "提交工序转移")
    @PostMapping("/transfers")
    public ApiResult<MesTransfer> submitTransfer(@RequestBody MesTransferSubmitRequest request) {
        return ApiResult.ok("转移成功", reportService.submitTransfer(request));
    }

    @Operation(summary = "转移单列表")
    @GetMapping("/transfers")
    public ApiResult<PageResult<MesTransfer>> transfers(
            @RequestParam(required = false) String transferNo,
            @RequestParam(required = false) String moNo,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(reportService.pageTransfer(transferNo, moNo, syncStatus, current, size));
    }

    @Operation(summary = "转移单重试")
    @PostMapping("/transfers/{transferNo}/retry")
    public ApiResult<Void> retryTransfer(@PathVariable String transferNo) {
        reportService.retryTransfer(transferNo);
        return ApiResult.ok("已加入重试队列", null);
    }
}
