package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.stockcheck.entity.StockcheckDetail;
import com.wms.stockcheck.entity.StockcheckTask;
import com.wms.stockcheck.service.StockcheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "PDA-盘点")
@RestController
@RequestMapping("/mobile/stockcheck")
@RequiredArgsConstructor
public class MobileStockcheckController {

    private final StockcheckService stockcheckService;

    @Operation(summary = "盘点任务详情")
    @GetMapping("/{taskNo}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String taskNo) {
        StockcheckTask task = stockcheckService.getTask(taskNo);
        List<StockcheckDetail> details = stockcheckService.getTaskDetails(taskNo);
        Map<String, Object> data = new HashMap<>();
        data.put("task", task);
        data.put("taskId", taskNo);
        data.put("details", details);
        return ApiResult.ok(data);
    }

    @Operation(summary = "扫码盘点提交")
    @PostMapping("/{taskNo}/scan")
    public ApiResult<Map<String, Object>> scan(@PathVariable String taskNo,
                                               @RequestBody StockcheckScanRequest request) {
        if (request.getLineNo() != null) {
            stockcheckService.submitCount(taskNo, request.getLineNo(), request.getActualQty());
            return ApiResult.ok("盘点录入成功", Map.of("taskId", taskNo));
        }
        Map<String, Object> data = stockcheckService.submitCountByScan(
                taskNo, request.getLocationCode(), request.getMaterialCode(),
                request.getBatchNo(), request.getActualQty());
        return ApiResult.ok("盘点录入成功", data);
    }

    @Operation(summary = "盘盈录入")
    @PostMapping("/{taskNo}/gain")
    public ApiResult<Void> gain(@PathVariable String taskNo, @RequestBody StockcheckGainRequest request) {
        stockcheckService.recordGain(taskNo, request.getLocationCode(), request.getMaterialCode(),
                request.getBatchNo(), request.getActualQty(), request.getRemark());
        return ApiResult.ok("盘盈录入成功", null);
    }

    @Operation(summary = "确认无库存")
    @PostMapping("/{taskNo}/confirm-empty")
    public ApiResult<Void> confirmEmpty(@PathVariable String taskNo,
                                        @RequestBody StockcheckEmptyRequest request) {
        stockcheckService.confirmEmpty(taskNo, request.getLocationCode(),
                request.getMaterialCode(), request.getBatchNo());
        return ApiResult.ok("已确认无库存", null);
    }

    @Operation(summary = "完成盘点任务")
    @PostMapping("/{taskNo}/complete")
    public ApiResult<Void> complete(@PathVariable String taskNo) {
        stockcheckService.completeTask(taskNo);
        return ApiResult.ok("盘点完成", null);
    }

    @Data
    public static class StockcheckScanRequest {
        private Integer lineNo;
        private String locationCode;
        private String materialCode;
        private String batchNo;
        private BigDecimal actualQty;
        private String barcodeContent;
        private String diffReason;
        private String deviceNo;
    }

    @Data
    public static class StockcheckGainRequest {
        private String locationCode;
        private String materialCode;
        private String batchNo;
        private BigDecimal actualQty;
        private String remark;
        private String deviceNo;
    }

    @Data
    public static class StockcheckEmptyRequest {
        private String locationCode;
        private String materialCode;
        private String batchNo;
        private String deviceNo;
    }
}
