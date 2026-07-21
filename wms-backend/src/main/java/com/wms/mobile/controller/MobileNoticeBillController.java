package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.noticebill.NoticeBillDirection;
import com.wms.noticebill.NoticeBillType;
import com.wms.pdareceive.dto.*;
import com.wms.pdareceive.service.PdaReceiveScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * PDA 通知单统一扫码入/出库（按单据类型）。
 */
@Tag(name = "PDA-通知单扫码")
@RestController
@RequestMapping("/mobile/notice-bill")
@RequiredArgsConstructor
public class MobileNoticeBillController {

    private final PdaReceiveScanService noticeScanService;

    @Operation(summary = "获取支持的通知单类型")
    @GetMapping("/types")
    public ApiResult<List<Map<String, String>>> listTypes(
            @RequestParam(required = false) String direction) {
        List<NoticeBillType> types;
        if (StringUtils.hasText(direction)) {
            NoticeBillDirection dir = "OUTBOUND".equalsIgnoreCase(direction.trim())
                    ? NoticeBillDirection.OUTBOUND : NoticeBillDirection.INBOUND;
            types = NoticeBillType.byDirection(dir);
        } else {
            types = Arrays.asList(NoticeBillType.values());
        }
        List<Map<String, String>> result = types.stream()
                .map(t -> Map.of(
                        "code", t.getCode(),
                        "label", t.getLabel(),
                        "direction", t.getDirection().name()))
                .toList();
        return ApiResult.ok(result);
    }

    @Operation(summary = "通知单列表")
    @GetMapping("/{billType}")
    public ApiResult<PageResult<ReceiveNoticeListItemVo>> list(
            @PathVariable String billType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "50") long size) {
        return ApiResult.ok(noticeScanService.list(NoticeBillType.fromCode(billType), keyword, current, size));
    }

    @Operation(summary = "解析条码获取单号")
    @PostMapping("/{billType}/resolve-barcode")
    public ApiResult<Map<String, String>> resolveBarcode(
            @PathVariable String billType,
            @RequestBody Map<String, String> body) {
        String no = noticeScanService.resolveBillNoFromBarcode(
                NoticeBillType.fromCode(billType), body.get("barcodeContent"));
        return ApiResult.ok(Map.of("billNo", no != null ? no : ""));
    }

    @Operation(summary = "通知单详情（含扫码进度）")
    @GetMapping("/{billType}/{billNo}")
    public ApiResult<ReceiveNoticeDetailVo> detail(
            @PathVariable String billType,
            @PathVariable String billNo,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResult.ok(noticeScanService.getDetail(NoticeBillType.fromCode(billType), billNo, refresh));
    }

    @Operation(summary = "扫描物料条码")
    @PostMapping("/{billType}/{billNo}/scan")
    public ApiResult<ReceiveNoticeLineVo> scan(
            @PathVariable String billType,
            @PathVariable String billNo,
            @Valid @RequestBody ReceiveNoticeScanRequest request) {
        return ApiResult.ok("扫描成功",
                noticeScanService.scanLine(NoticeBillType.fromCode(billType), billNo, request));
    }

    @Operation(summary = "勾选/取消明细行")
    @PutMapping("/{billType}/{billNo}/lines/{lineNo}/check")
    public ApiResult<ReceiveNoticeLineVo> toggleCheck(
            @PathVariable String billType,
            @PathVariable String billNo,
            @PathVariable Integer lineNo,
            @RequestParam boolean checked) {
        return ApiResult.ok(noticeScanService.toggleLine(
                NoticeBillType.fromCode(billType), billNo, lineNo, checked));
    }

    @Operation(summary = "修改本次领取数量")
    @PutMapping("/{billType}/{billNo}/lines/{lineNo}/qty")
    public ApiResult<ReceiveNoticeLineVo> updateQty(
            @PathVariable String billType,
            @PathVariable String billNo,
            @PathVariable Integer lineNo,
            @Valid @RequestBody ReceiveNoticeQtyRequest request) {
        return ApiResult.ok("数量已更新",
                noticeScanService.updateLineQty(NoticeBillType.fromCode(billType), billNo, lineNo, request));
    }

    @Operation(summary = "部分提交（入库增加库存 / 出库扣减库存）")
    @PostMapping("/{billType}/{billNo}/submit")
    public ApiResult<Map<String, Object>> submit(
            @PathVariable String billType,
            @PathVariable String billNo,
            @RequestBody(required = false) ReceiveNoticeSubmitRequest request) {
        if (request == null) {
            request = new ReceiveNoticeSubmitRequest();
        }
        return ApiResult.ok("提交成功",
                noticeScanService.submit(NoticeBillType.fromCode(billType), billNo, request));
    }
}
