package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.picking.dto.WorkshopReturnCreateRequest;
import com.wms.picking.dto.PickIssueScanRequest;
import com.wms.picking.entity.PickIssue;
import com.wms.picking.service.MaterialPickupService;
import com.wms.picking.service.PickIssueService;
import com.wms.picking.service.WorkshopReturnService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "PDA拣配")
@RestController
@RequestMapping("/mobile/picking")
@RequiredArgsConstructor
public class MobilePickingController {

    private final MaterialPickupService materialPickupService;
    private final PickIssueService pickIssueService;
    private final WorkshopReturnService workshopReturnService;

    @GetMapping("/issues")
    public ApiResult<List<PickIssue>> listIssues(
            @RequestParam(defaultValue = "PICKING") String status,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.ok(pickIssueService.listByStatus(status, limit));
    }

    @GetMapping("/issues/{issueNo}")
    public ApiResult<Map<String, Object>> issueDetail(@PathVariable String issueNo) {
        return ApiResult.ok(pickIssueService.detail(issueNo));
    }

    @PostMapping("/issues/{issueNo}/scan")
    public ApiResult<Map<String, Object>> scanPick(@PathVariable String issueNo,
                                                    @Valid @RequestBody PickIssueScanRequest request) {
        return ApiResult.ok("拣货成功", pickIssueService.scanPick(issueNo, request));
    }

    @PostMapping("/pickup/confirm")
    public ApiResult<String> confirmPickup(@RequestBody PickupConfirmRequest request,
                                           @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : request.getReceiverName();
        String issueNo = request.getIssueNo();
        if (issueNo != null && issueNo.startsWith("PI:")) {
            issueNo = issueNo.substring(3);
        }
        return ApiResult.ok("领料确认成功", materialPickupService.confirmByIssueQr(issueNo, name));
    }

    @PostMapping("/workshop-return")
    public ApiResult<String> createWorkshopReturn(@RequestBody MobileWorkshopReturnRequest request,
                                                    @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "pda";
        WorkshopReturnCreateRequest req = new WorkshopReturnCreateRequest();
        req.setIssueNo(request.getIssueNo());
        req.setWarehouseCode(request.getWarehouseCode());
        req.setReturnReason(request.getReturnReason());
        WorkshopReturnCreateRequest.LineItem line = new WorkshopReturnCreateRequest.LineItem();
        line.setMaterialCode(request.getMaterialCode());
        line.setReturnQty(request.getReturnQty() != null ? request.getReturnQty() : BigDecimal.ONE);
        line.setBatchNo(request.getBatchNo());
        req.setLines(List.of(line));
        String returnNo = workshopReturnService.create(req, name);
        if (Boolean.TRUE.equals(request.getAutoConfirm())) {
            workshopReturnService.confirm(returnNo, name);
        }
        return ApiResult.ok("退库单已创建", returnNo);
    }

    @Data
    public static class PickupConfirmRequest {
        private String issueNo;
        private String receiverName;
    }

    @Data
    public static class MobileWorkshopReturnRequest {
        private String issueNo;
        private String warehouseCode;
        private String materialCode;
        private String batchNo;
        private BigDecimal returnQty;
        private String returnReason;
        private Boolean autoConfirm;
    }
}
