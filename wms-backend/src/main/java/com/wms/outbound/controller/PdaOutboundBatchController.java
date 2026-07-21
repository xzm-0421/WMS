package com.wms.outbound.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.pdareceive.entity.PdaReceiveSubmitBatch;
import com.wms.pdareceive.service.PdaReceiveSubmitBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "PDA出库记录")
@RestController
@RequestMapping("/outbound/pda-batches")
@RequiredArgsConstructor
public class PdaOutboundBatchController {

    private final PdaReceiveSubmitBatchService batchService;

    @Operation(summary = "PDA出库提交批次列表")
    @GetMapping
    public ApiResult<PageResult<PdaReceiveSubmitBatch>> list(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String erpSyncStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(batchService.page(
                billNo, erpSyncStatus, "OUTBOUND", billType, batchNo, current, size));
    }

    @Operation(summary = "PDA出库批次详情（含库存扣减明细）")
    @GetMapping("/{batchNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String batchNo) {
        return ApiResult.ok(batchService.getOutboundBatchDetail(batchNo));
    }

    @Operation(summary = "同步出库批次至金蝶生产领料单")
    @PostMapping("/{batchNo}/sync-erp")
    public ApiResult<Map<String, Object>> syncErp(@PathVariable String batchNo) {
        return ApiResult.ok("同步完成", batchService.syncOutboundBatchToErp(batchNo));
    }
}
