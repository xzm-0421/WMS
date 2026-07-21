package com.wms.print.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.print.dto.KingdeeLabelPrintRequest;
import com.wms.print.dto.LabelPrintJobVo;
import com.wms.print.service.LabelPrintJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "物料标签打印")
@RestController
@RequestMapping("/print/label-jobs")
@RequiredArgsConstructor
public class LabelPrintJobController {

    private final LabelPrintJobService labelPrintJobService;

    @Operation(summary = "打印任务列表")
    @GetMapping
    public ApiResult<PageResult<LabelPrintJobVo>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(labelPrintJobService.page(keyword, status, current, size));
    }

    @Operation(summary = "打印任务详情")
    @GetMapping("/{jobId}")
    public ApiResult<LabelPrintJobVo> detail(@PathVariable String jobId) {
        return ApiResult.ok(labelPrintJobService.getByJobId(jobId));
    }

    @Operation(summary = "手工创建打印任务")
    @PostMapping
    public ApiResult<LabelPrintJobVo> create(@Valid @RequestBody KingdeeLabelPrintRequest request) {
        return ApiResult.ok("任务已创建", labelPrintJobService.createManual(request));
    }

    @Operation(summary = "同步物料主数据到打印任务")
    @PostMapping("/{jobId}/sync-material")
    public ApiResult<LabelPrintJobVo> syncMaterial(@PathVariable String jobId) {
        return ApiResult.ok("已同步物料信息", labelPrintJobService.syncMaterial(jobId));
    }

    @Operation(summary = "标记预览已打开")
    @PostMapping("/{jobId}/open")
    public ApiResult<Void> markOpen(@PathVariable String jobId) {
        labelPrintJobService.markOpened(jobId);
        return ApiResult.ok(null);
    }

    @Operation(summary = "确认打印完成")
    @PostMapping("/{jobId}/printed")
    public ApiResult<LabelPrintJobVo> markPrinted(
            @PathVariable String jobId,
            @RequestBody(required = false) Map<String, String> body) {
        String error = body != null ? body.get("errorMessage") : null;
        return ApiResult.ok("已记录", labelPrintJobService.markPrinted(jobId, error));
    }

    @Operation(summary = "更新打印参数")
    @PutMapping("/{jobId}/settings")
    public ApiResult<LabelPrintJobVo> updateSettings(
            @PathVariable String jobId,
            @RequestBody Map<String, Object> body) {
        BigDecimal width = body.get("labelWidthMm") != null
                ? new BigDecimal(body.get("labelWidthMm").toString()) : null;
        BigDecimal height = body.get("labelHeightMm") != null
                ? new BigDecimal(body.get("labelHeightMm").toString()) : null;
        Integer copies = body.get("copies") != null
                ? Integer.valueOf(body.get("copies").toString()) : null;
        String barcodeType = body.get("barcodeType") != null
                ? body.get("barcodeType").toString() : null;
        return ApiResult.ok(labelPrintJobService.updateSettings(jobId, width, height, copies, barcodeType));
    }
}
