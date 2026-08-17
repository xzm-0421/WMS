package com.wms.pdareceive.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.pdareceive.entity.PdaReceiveSubmitBatch;
import com.wms.pdareceive.service.PdaReceiveSubmitBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "收料入库提交批次")
@RestController
@RequestMapping("/inbound/receive-batches")
@RequiredArgsConstructor
public class PdaReceiveSubmitBatchController {

    private final PdaReceiveSubmitBatchService batchService;

    @Operation(summary = "收料部分入库提交批次列表")
    @GetMapping
    public ApiResult<PageResult<PdaReceiveSubmitBatch>> list(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String erpSyncStatus,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        // 默认只查入库批次，避免与 PDA 出库记录混在一起
        String dir = StringUtils.hasText(direction) ? direction : "INBOUND";
        return ApiResult.ok(batchService.page(billNo, erpSyncStatus, dir, null, null, current, size));
    }

    @Operation(summary = "收料入库批次详情（明细 + 同步报错）")
    @GetMapping("/{batchNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String batchNo) {
        return ApiResult.ok(batchService.getInboundBatchDetail(batchNo));
    }

    @Operation(summary = "批次同步金蝶生成入库单")
    @PostMapping("/{batchNo}/sync-erp")
    public ApiResult<Map<String, Object>> syncErp(@PathVariable String batchNo) {
        return ApiResult.ok("同步完成", batchService.syncBatchToErp(batchNo));
    }
}
