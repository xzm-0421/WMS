package com.wms.integration.kingdee.controller;

import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.ApiResult;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.print.dto.KingdeeLabelPrintBatchRequest;
import com.wms.print.dto.KingdeeLabelPrintBatchResult;
import com.wms.print.dto.KingdeeLabelPrintRequest;
import com.wms.print.dto.LabelPrintJobVo;
import com.wms.print.service.LabelPrintJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * 金蝶云星空企业版 → WMS 物料标签打印接入（免登录，可选 API Key）。
 */
@Tag(name = "金蝶-标签打印")
@RestController
@RequestMapping("/integration/kingdee/label-print")
@RequiredArgsConstructor
public class KingdeeLabelPrintController {

    private final LabelPrintJobService labelPrintJobService;
    private final KingdeeCloudProperties kingdeeProperties;

    @Operation(summary = "金蝶发起单张物料标签打印")
    @PostMapping
    public ApiResult<LabelPrintJobVo> receivePrintRequest(
            @RequestHeader(value = "X-Kingdee-Api-Key", required = false) String apiKey,
            @Valid @RequestBody KingdeeLabelPrintRequest request) {
        validateApiKey(apiKey);
        LabelPrintJobVo job = labelPrintJobService.createFromKingdee(request, "KINGDEE");
        return ApiResult.ok("打印任务已创建", job);
    }

    @Operation(summary = "金蝶批量发起物料标签打印")
    @PostMapping("/batch")
    public ApiResult<KingdeeLabelPrintBatchResult> receiveBatchPrintRequest(
            @RequestHeader(value = "X-Kingdee-Api-Key", required = false) String apiKey,
            @Valid @RequestBody KingdeeLabelPrintBatchRequest request) {
        validateApiKey(apiKey);
        return ApiResult.ok("打印任务已创建", labelPrintJobService.createBatchFromKingdee(request));
    }

    @Operation(summary = "免登录获取标签打印数据（供 print-designer 使用）")
    @GetMapping("/jobs/{jobId}/data")
    public ApiResult<Map<String, Object>> printData(
            @RequestHeader(value = "X-Kingdee-Api-Key", required = false) String apiKey,
            @PathVariable String jobId) {
        validateApiKey(apiKey);
        return ApiResult.ok(labelPrintJobService.buildPrintDocument(jobId));
    }

    @Operation(summary = "免登录：打开打印页时标记任务")
    @PostMapping("/jobs/{jobId}/open")
    public ApiResult<Void> markOpened(
            @RequestHeader(value = "X-Kingdee-Api-Key", required = false) String apiKey,
            @PathVariable String jobId) {
        validateApiKey(apiKey);
        labelPrintJobService.markOpened(jobId);
        return ApiResult.ok("ok", null);
    }

    @Operation(summary = "免登录：打印完成/失败回调")
    @PostMapping("/jobs/{jobId}/printed")
    public ApiResult<LabelPrintJobVo> markPrinted(
            @RequestHeader(value = "X-Kingdee-Api-Key", required = false) String apiKey,
            @PathVariable String jobId,
            @RequestBody(required = false) Map<String, Object> body) {
        validateApiKey(apiKey);
        String error = null;
        if (body != null && body.get("errorMessage") != null) {
            error = String.valueOf(body.get("errorMessage"));
        }
        return ApiResult.ok(labelPrintJobService.markPrinted(jobId, error));
    }

    @Operation(summary = "302 跳转到打印预览页（金蝶按钮可直接打开此 URL）")
    @GetMapping("/jobs/{jobId}/open-print")
    public void openPrint(
            @RequestHeader(value = "X-Kingdee-Api-Key", required = false) String apiKey,
            @PathVariable String jobId,
            @RequestParam(defaultValue = "true") boolean autoPrint,
            HttpServletResponse response) throws IOException {
        validateApiKey(apiKey);
        LabelPrintJobVo job = labelPrintJobService.getByJobId(jobId);
        String target = StringUtils.hasText(job.getAbsolutePrintUrl())
                ? job.getAbsolutePrintUrl()
                : job.getPrintUrl();
        if (!autoPrint && StringUtils.hasText(target)) {
            target = target.replace("&autoPrint=1", "").replace("?autoPrint=1&", "?").replace("?autoPrint=1", "");
        } else if (autoPrint && StringUtils.hasText(target) && !target.contains("autoPrint=")) {
            target = target + (target.contains("?") ? "&" : "?") + "autoPrint=1";
        }
        if (!StringUtils.hasText(target)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "打印地址为空");
        }
        if (target.startsWith("/")) {
            String base = kingdeeProperties.getPrintPublicBaseUrl();
            if (StringUtils.hasText(base)) {
                target = base.trim().replaceAll("/+$", "") + target;
            }
        }
        labelPrintJobService.markOpened(jobId);
        response.sendRedirect(target);
    }

    @Operation(summary = "金蝶打印回调健康检查")
    @GetMapping("/health")
    public ApiResult<Map<String, String>> health() {
        return ApiResult.ok(Map.of(
                "status", "UP",
                "product", "Kingdee Cloud Galaxy Enterprise",
                "module", "label-print",
                "publicBaseUrl", StringUtils.hasText(kingdeeProperties.getPrintPublicBaseUrl())
                        ? kingdeeProperties.getPrintPublicBaseUrl()
                        : ""));
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
