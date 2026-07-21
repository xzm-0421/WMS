package com.wms.integration.kingdee.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.ApiResult;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.print.dto.KingdeeLabelPrintRequest;
import com.wms.print.dto.LabelPrintJobVo;
import com.wms.print.service.LabelPrintJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 金蝶云星空企业版 → WMS 物料标签打印接入
 */
@Tag(name = "金蝶-标签打印")
@RestController
@RequestMapping("/integration/kingdee/label-print")
@RequiredArgsConstructor
public class KingdeeLabelPrintController {

    private final LabelPrintJobService labelPrintJobService;
    private final KingdeeCloudProperties kingdeeProperties;
    private final ObjectMapper objectMapper;

    @Operation(summary = "金蝶发起物料标签打印（API Key）")
    @PostMapping
    public ApiResult<LabelPrintJobVo> receivePrintRequest(
            @RequestHeader(value = "X-Kingdee-Api-Key", required = false) String apiKey,
            @Valid @RequestBody KingdeeLabelPrintRequest request) {
        validateApiKey(apiKey);
        LabelPrintJobVo job = labelPrintJobService.createFromKingdee(request, "KINGDEE");
        return ApiResult.ok("打印任务已创建", job);
    }

    @Operation(summary = "金蝶打印回调健康检查")
    @GetMapping("/health")
    public ApiResult<Map<String, String>> health() {
        return ApiResult.ok(Map.of(
                "status", "UP",
                "product", "Kingdee Cloud Galaxy Enterprise",
                "module", "label-print"));
    }

    private void validateApiKey(String apiKey) {
        String configured = kingdeeProperties.getPrintApiKey();
        if (!StringUtils.hasText(configured)) {
            return;
        }
        if (!configured.equals(apiKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "金蝶 API Key 无效");
        }
    }
}
