package com.wms.pdainbound.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.integration.entity.ErpSyncLog;
import com.wms.pdainbound.dto.PdaInboundRecordVo;
import com.wms.pdainbound.dto.PdaInboundReverseRequest;
import com.wms.pdainbound.service.PdaInboundRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "PDA入库记录管理")
@RestController
@RequestMapping("/inbound/pda-records")
@RequiredArgsConstructor
public class PdaInboundRecordController {

    private final PdaInboundRecordService recordService;

    @Operation(summary = "PDA入库记录列表")
    @GetMapping
    public ApiResult<PageResult<PdaInboundRecordVo>> list(
            @RequestParam(required = false) String recordNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String erpSyncStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(recordService.page(recordNo, materialCode, warehouseCode, status, erpSyncStatus, current, size));
    }

    @Operation(summary = "PDA入库记录详情")
    @GetMapping("/{recordNo}")
    public ApiResult<PdaInboundRecordVo> detail(@PathVariable String recordNo) {
        return ApiResult.ok(recordService.getByRecordNo(recordNo));
    }

    @Operation(summary = "审核PDA入库记录")
    @PutMapping("/{recordNo}/audit")
    public ApiResult<Void> audit(@PathVariable String recordNo) {
        recordService.audit(recordNo);
        return ApiResult.ok("审核成功", null);
    }

    @Operation(summary = "冲销PDA入库记录")
    @PutMapping("/{recordNo}/reverse")
    public ApiResult<Void> reverse(@PathVariable String recordNo, @RequestBody(required = false) PdaInboundReverseRequest body) {
        recordService.reverse(recordNo, body != null ? body.getReason() : null);
        return ApiResult.ok("冲销成功", null);
    }

    @Operation(summary = "重新同步金蝶")
    @PostMapping("/{recordNo}/resync")
    public ApiResult<PdaInboundRecordVo> resync(@PathVariable String recordNo) {
        return ApiResult.ok("同步完成", recordService.resyncErp(recordNo));
    }

    @Operation(summary = "金蝶同步日志")
    @GetMapping("/{recordNo}/sync-logs")
    public ApiResult<List<ErpSyncLog>> syncLogs(@PathVariable String recordNo) {
        return ApiResult.ok(recordService.syncLogs(recordNo));
    }
}
