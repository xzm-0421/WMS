package com.wms.print.controller;

import com.wms.common.excel.ExcelHttpHelper;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.print.dto.KingdeeLabelPrintRequest;
import com.wms.print.dto.LabelPrintJobVo;
import com.wms.print.service.LabelPrintJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "期初库存标签打印")
@RestController
@RequestMapping("/print/label-jobs")
@RequiredArgsConstructor
public class LabelPrintJobController {

    private final LabelPrintJobService labelPrintJobService;

    @Operation(summary = "期初库存打印任务列表")
    @GetMapping
    public ApiResult<PageResult<LabelPrintJobVo>> list(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) String materialKeyword,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        String material = StringUtils.hasText(materialKeyword) ? materialKeyword : keyword;
        return ApiResult.ok(labelPrintJobService.page(warehouseCode, warehouseName, material, current, size));
    }

    @Operation(summary = "下载期初库存导入模板")
    @GetMapping("/import/template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpHelper.writeXlsx(response, "期初库存导入模板.xlsx",
                labelPrintJobService::writeImportTemplate);
    }

    @Operation(summary = "导入期初库存 Excel")
    @PostMapping("/import")
    public ApiResult<Integer> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        int count = labelPrintJobService.importExcel(file);
        return ApiResult.ok("成功导入 " + count + " 条", count);
    }

    @Operation(summary = "导出期初库存 Excel")
    @GetMapping("/export")
    public void exportExcel(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) String materialKeyword,
            @RequestParam(required = false) String keyword,
            HttpServletResponse response) throws IOException {
        String material = StringUtils.hasText(materialKeyword) ? materialKeyword : keyword;
        ExcelHttpHelper.writeXlsx(response, "期初库存.xlsx",
                out -> labelPrintJobService.exportExcel(warehouseCode, warehouseName, material, out));
    }

    @Operation(summary = "批量删除期初库存数据")
    @DeleteMapping("/opening")
    public ApiResult<Integer> deleteOpeningStock(@RequestBody List<Long> ids) {
        int count = labelPrintJobService.deleteOpeningStock(ids);
        return ApiResult.ok("已删除 " + count + " 条", count);
    }

    @Operation(summary = "手工创建打印任务")
    @PostMapping
    public ApiResult<LabelPrintJobVo> create(@Valid @RequestBody KingdeeLabelPrintRequest request) {
        return ApiResult.ok("任务已创建", labelPrintJobService.createManual(request));
    }

    @Operation(summary = "打印任务详情")
    @GetMapping("/{jobId}")
    public ApiResult<LabelPrintJobVo> detail(@PathVariable String jobId) {
        return ApiResult.ok(labelPrintJobService.getByJobId(jobId));
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
