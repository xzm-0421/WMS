package com.wms.barcode.controller;

import com.wms.barcode.dto.*;
import com.wms.barcode.entity.BarcodeArchive;
import com.wms.barcode.entity.BarcodeRule;
import com.wms.barcode.entity.BarcodeRuleVersion;
import com.wms.barcode.service.BarcodeArchiveService;
import com.wms.barcode.service.BarcodeGenerateService;
import com.wms.barcode.service.BarcodeInstanceService;
import com.wms.barcode.service.BarcodeService;
import com.wms.barcode.service.BarcodeTraceService;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.integration.kingdee.KingdeeBarcodeSourceService;
import com.wms.integration.kingdee.dto.KingdeeBatchVo;
import com.wms.integration.kingdee.dto.KingdeeMaterialBarcodeVo;
import com.wms.integration.kingdee.dto.KingdeeSerialVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "条码管理")
@RestController
@RequestMapping("/barcode")
@RequiredArgsConstructor
public class BarcodeController {

    private final BarcodeService barcodeService;
    private final BarcodeGenerateService generateService;
    private final BarcodeTraceService traceService;
    private final KingdeeBarcodeSourceService kingdeeBarcodeSourceService;
    private final BarcodeArchiveService archiveService;
    private final BarcodeInstanceService instanceService;

    @Operation(summary = "条码规则列表")
    @GetMapping("/rules")
    public ApiResult<PageResult<BarcodeRule>> list(
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String appliesTo,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(barcodeService.page(ruleCode, ruleName, appliesTo, status, current, size));
    }

    @Operation(summary = "预置拼接模板")
    @GetMapping("/templates")
    public ApiResult<Map<String, String>> templates() {
        return ApiResult.ok(barcodeService.templateOptions());
    }

    @Operation(summary = "条码规则详情")
    @GetMapping("/rules/{ruleCode}")
    public ApiResult<BarcodeRule> detail(@PathVariable String ruleCode) {
        return ApiResult.ok(barcodeService.getByCode(ruleCode));
    }

    @Operation(summary = "规则版本历史")
    @GetMapping("/rules/{ruleCode}/versions")
    public ApiResult<List<BarcodeRuleVersion>> versions(@PathVariable String ruleCode) {
        return ApiResult.ok(barcodeService.listVersions(ruleCode));
    }

    @Operation(summary = "规则指定版本详情")
    @GetMapping("/rules/{ruleCode}/versions/{versionNo}")
    public ApiResult<BarcodeRuleVersion> versionDetail(@PathVariable String ruleCode,
                                                        @PathVariable int versionNo) {
        return ApiResult.ok(barcodeService.getVersion(ruleCode, versionNo));
    }

    @Operation(summary = "创建条码规则")
    @PostMapping("/rules")
    public ApiResult<Void> create(@RequestBody BarcodeRule rule) {
        barcodeService.create(rule);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新条码规则（自动升版）")
    @PutMapping("/rules/{ruleCode}")
    public ApiResult<Void> update(@PathVariable String ruleCode,
                                   @RequestBody BarcodeRule rule,
                                   @RequestParam(required = false) String changeLog) {
        barcodeService.update(ruleCode, rule, changeLog);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "启用/禁用条码规则")
    @PutMapping("/rules/{ruleCode}/status")
    public ApiResult<Void> updateStatus(@PathVariable String ruleCode,
                                         @RequestBody BarcodeStatusRequest request) {
        barcodeService.updateStatus(ruleCode, request.getStatus(), request.getChangeLog());
        return ApiResult.ok("状态已更新", null);
    }

    @Operation(summary = "删除条码规则")
    @DeleteMapping("/rules/{ruleCode}")
    public ApiResult<Void> delete(@PathVariable String ruleCode) {
        barcodeService.delete(ruleCode);
        return ApiResult.ok("删除成功", null);
    }

    @Operation(summary = "解析条码")
    @PostMapping("/parse")
    public ApiResult<Map<String, Object>> parse(@Valid @RequestBody BarcodeParseRequest request) {
        return ApiResult.ok(barcodeService.parse(request));
    }

    @Operation(summary = "按规则生成条码")
    @PostMapping("/generate")
    public ApiResult<BarcodeGenerateResult> generate(@Valid @RequestBody BarcodeGenerateRequest request) {
        return ApiResult.ok(generateService.generate(request));
    }

    @Operation(summary = "条码追溯")
    @GetMapping("/trace")
    public ApiResult<BarcodeTraceResult> trace(@RequestParam String barcodeContent) {
        return ApiResult.ok(traceService.trace(barcodeContent));
    }

    @Operation(summary = "条码存档列表")
    @GetMapping("/archives")
    public ApiResult<PageResult<BarcodeArchiveVo>> listArchives(
            @RequestParam(required = false) String archiveNo,
            @RequestParam(required = false) String barcodeContent,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createTimeFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createTimeTo,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(archiveService.page(archiveNo, barcodeContent, materialCode, ruleCode,
                createTimeFrom, createTimeTo, current, size));
    }

    @Operation(summary = "条码存档详情")
    @GetMapping("/archives/{archiveNo}")
    public ApiResult<BarcodeArchiveVo> archiveDetail(@PathVariable String archiveNo) {
        return ApiResult.ok(archiveService.getByArchiveNo(archiveNo));
    }

    @Operation(summary = "从存档重新打印（记录补打次数）")
    @PostMapping("/archives/{archiveNo}/reprint")
    public ApiResult<BarcodeReprintResult> reprint(@PathVariable String archiveNo) {
        return ApiResult.ok(archiveService.reprint(archiveNo));
    }

    @Operation(summary = "更新条码实例并写入存档")
    @PutMapping("/instances/{id}")
    public ApiResult<BarcodeArchiveVo> updateInstance(@PathVariable Long id,
                                                       @RequestBody BarcodeInstanceUpdateRequest request) {
        BarcodeArchive archive = instanceService.update(id, request);
        return ApiResult.ok(archiveService.getByArchiveNo(archive.getArchiveNo()));
    }

    @Operation(summary = "手动清理过期条码存档")
    @PostMapping("/archives/cleanup")
    public ApiResult<Map<String, Object>> cleanupArchives() {
        int removed = archiveService.cleanupExpired();
        return ApiResult.ok(Map.of("removed", removed));
    }

    @Operation(summary = "金蝶物料条码数据")
    @GetMapping("/kingdee/materials")
    public ApiResult<PageResult<KingdeeMaterialBarcodeVo>> kingdeeMaterials(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(kingdeeBarcodeSourceService.pageMaterials(keyword, current, size));
    }

    @Operation(summary = "金蝶批号数据")
    @GetMapping("/kingdee/batches")
    public ApiResult<PageResult<KingdeeBatchVo>> kingdeeBatches(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(kingdeeBarcodeSourceService.pageBatches(materialCode, keyword, current, size));
    }

    @Operation(summary = "金蝶序列号数据")
    @GetMapping("/kingdee/serials")
    public ApiResult<PageResult<KingdeeSerialVo>> kingdeeSerials(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(kingdeeBarcodeSourceService.pageSerials(materialCode, batchNo, keyword, current, size));
    }
}
