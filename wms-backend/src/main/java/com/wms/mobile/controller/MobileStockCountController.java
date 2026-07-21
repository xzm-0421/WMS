package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.pdastockcount.dto.StockCountDetailVo;
import com.wms.pdastockcount.dto.StockCountLineVo;
import com.wms.pdastockcount.dto.StockCountListItemVo;
import com.wms.pdastockcount.dto.StockCountQtyRequest;
import com.wms.pdastockcount.dto.StockCountScanRequest;
import com.wms.pdastockcount.service.PdaStockCountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "PDA-金蝶盘点作业")
@RestController
@RequestMapping("/mobile/stock-count")
@RequiredArgsConstructor
public class MobileStockCountController {

    private final PdaStockCountService stockCountService;

    @Operation(summary = "盘点作业列表（仅已审核）")
    @GetMapping
    public ApiResult<PageResult<StockCountListItemVo>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "50") long size) {
        return ApiResult.ok(stockCountService.list(keyword, current, size));
    }

    @Operation(summary = "解析盘点二维码获取单号")
    @PostMapping("/resolve-barcode")
    public ApiResult<Map<String, String>> resolveBarcode(@RequestBody Map<String, String> body) {
        String billNo = stockCountService.resolveBillNoFromBarcode(
                body != null ? body.get("barcodeContent") : null);
        return ApiResult.ok(Map.of("billNo", billNo != null ? billNo : ""));
    }

    @Operation(summary = "盘点作业详情（含明细与实盘进度）")
    @GetMapping("/{billNo}")
    public ApiResult<StockCountDetailVo> detail(
            @PathVariable String billNo,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        return ApiResult.ok(stockCountService.getDetail(billNo, forceRefresh));
    }

    @Operation(summary = "扫描物料条码匹配明细（实时反馈，不落库）")
    @PostMapping("/{billNo}/match")
    public ApiResult<StockCountLineVo> match(
            @PathVariable String billNo,
            @RequestBody StockCountScanRequest request) {
        return ApiResult.ok(stockCountService.match(billNo, request));
    }

    @Operation(summary = "扫描物料条码匹配并录入实盘数量")
    @PostMapping("/{billNo}/scan")
    public ApiResult<StockCountLineVo> scan(
            @PathVariable String billNo,
            @RequestBody StockCountScanRequest request) {
        return ApiResult.ok("盘点录入成功", stockCountService.scan(billNo, request));
    }

    @Operation(summary = "手动修正明细实盘数量")
    @PutMapping("/{billNo}/lines/{lineNo}/qty")
    public ApiResult<StockCountLineVo> updateQty(
            @PathVariable String billNo,
            @PathVariable Integer lineNo,
            @Valid @RequestBody StockCountQtyRequest request) {
        return ApiResult.ok("数量已更新", stockCountService.updateQty(billNo, lineNo, request));
    }

    @Operation(summary = "完成盘点")
    @PostMapping("/{billNo}/complete")
    public ApiResult<Map<String, Object>> complete(@PathVariable String billNo) {
        return ApiResult.ok("盘点完成", stockCountService.complete(billNo));
    }
}
