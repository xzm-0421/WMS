package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.mobile.dto.MobileScanRecognizeRequest;
import com.wms.mobile.service.MobileScanService;
import com.wms.pdainbound.dto.PdaInboundRecordVo;
import com.wms.pdainbound.dto.PdaInboundSubmitRequest;
import com.wms.pdainbound.service.PdaInboundRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "PDA-无单入库")
@RestController
@RequestMapping("/mobile/pda-inbound")
@RequiredArgsConstructor
public class MobilePdaInboundController {

    private final PdaInboundRecordService recordService;
    private final MobileScanService scanService;

    @Operation(summary = "条码识别(物料信息)")
    @PostMapping("/recognize")
    public ApiResult<Map<String, Object>> recognize(@RequestBody MobileScanRecognizeRequest request) {
        return ApiResult.ok(scanService.recognize(request));
    }

    @Operation(summary = "无单扫码入库提交")
    @PostMapping("/submit")
    public ApiResult<PdaInboundRecordVo> submit(@Valid @RequestBody PdaInboundSubmitRequest request) {
        return ApiResult.ok("入库成功", recordService.submit(request));
    }
}
