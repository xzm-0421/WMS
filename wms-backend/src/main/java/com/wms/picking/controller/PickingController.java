package com.wms.picking.controller;

import com.wms.common.excel.ExcelImportHelper;
import com.wms.common.excel.PrepNoticeImportRow;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.picking.dto.PrepNoticeCreateRequest;
import com.wms.picking.dto.WorkshopReturnCreateRequest;
import com.wms.picking.entity.MaterialPickup;
import com.wms.picking.entity.PickIssue;
import com.wms.picking.entity.PrepNotice;
import com.wms.picking.entity.WorkshopReturn;
import com.wms.picking.service.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "拣配管理")
@RestController
@RequestMapping("/picking")
@RequiredArgsConstructor
public class PickingController {

    private final PrepNoticeService prepNoticeService;
    private final PrepNoticeImportService prepNoticeImportService;
    private final PickIssueService pickIssueService;
    private final MaterialPickupService materialPickupService;
    private final WorkshopReturnService workshopReturnService;

    @GetMapping("/prep-notices")
    public ApiResult<PageResult<PrepNotice>> listPrepNotices(
            @RequestParam(required = false) String noticeNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(prepNoticeService.page(noticeNo, status, current, size));
    }

    @GetMapping("/prep-notices/{noticeNo}")
    public ApiResult<Map<String, Object>> prepNoticeDetail(@PathVariable String noticeNo) {
        return ApiResult.ok(prepNoticeService.detail(noticeNo));
    }

    @PostMapping("/prep-notices")
    public ApiResult<String> createPrepNotice(@RequestBody PrepNoticeCreateRequest request,
                                              @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", prepNoticeService.create(request, name));
    }

    @GetMapping("/prep-notices/import/template")
    public void prepNoticeImportTemplate(HttpServletResponse response) throws IOException {
        String fileName = URLEncoder.encode("备料通知导入模板.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        ExcelImportHelper.writeTemplate(response.getOutputStream(), PrepNoticeImportRow.class, "备料通知",
                List.of(PrepNoticeImportRow.sample()));
    }

    @PostMapping("/prep-notices/import")
    public ApiResult<List<String>> importPrepNotice(@RequestParam("file") MultipartFile file,
                                                     @AuthenticationPrincipal UserDetails user) throws Exception {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("导入成功", prepNoticeImportService.importExcel(file, name));
    }

    @GetMapping("/pick-issues")
    public ApiResult<PageResult<PickIssue>> listPickIssues(
            @RequestParam(required = false) String issueNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(pickIssueService.page(issueNo, status, current, size));
    }

    @GetMapping("/pick-issues/{issueNo}/print-data")
    public ApiResult<Map<String, Object>> pickIssuePrintData(@PathVariable String issueNo) {
        return ApiResult.ok(pickIssueService.buildPrintData(issueNo));
    }

    @GetMapping("/pick-issues/{issueNo}")
    public ApiResult<Map<String, Object>> pickIssueDetail(@PathVariable String issueNo) {
        return ApiResult.ok(pickIssueService.detail(issueNo));
    }

    @PostMapping("/pick-issues/from-notice/{noticeNo}")
    public ApiResult<String> createPickIssue(@PathVariable String noticeNo,
                                             @RequestParam(required = false) String handoverArea,
                                             @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", pickIssueService.createFromNotice(noticeNo, name, handoverArea));
    }

    @PostMapping("/pick-issues/{issueNo}/pickup-confirm")
    public ApiResult<String> confirmPickup(@PathVariable String issueNo,
                                           @RequestParam(required = false) String receiverName,
                                           @AuthenticationPrincipal UserDetails user) {
        String name = StringUtils.hasText(receiverName)
                ? receiverName
                : (user != null ? user.getUsername() : "system");
        return ApiResult.ok("领料确认成功", materialPickupService.confirmByIssueQr(issueNo, name));
    }

    @GetMapping("/material-pickups")
    public ApiResult<PageResult<MaterialPickup>> listPickups(
            @RequestParam(required = false) String issueNo,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(materialPickupService.page(issueNo, current, size));
    }

    @GetMapping("/workshop-returns")
    public ApiResult<PageResult<WorkshopReturn>> listWorkshopReturns(
            @RequestParam(required = false) String returnNo,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(workshopReturnService.page(returnNo, current, size));
    }

    @GetMapping("/workshop-returns/{returnNo}")
    public ApiResult<Map<String, Object>> workshopReturnDetail(@PathVariable String returnNo) {
        return ApiResult.ok(workshopReturnService.detail(returnNo));
    }

    @PostMapping("/workshop-returns")
    public ApiResult<String> createWorkshopReturn(@RequestBody WorkshopReturnCreateRequest request,
                                                  @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        return ApiResult.ok("创建成功", workshopReturnService.create(request, name));
    }

    @PostMapping("/workshop-returns/{returnNo}/confirm")
    public ApiResult<Void> confirmWorkshopReturn(@PathVariable String returnNo,
                                                 @AuthenticationPrincipal UserDetails user) {
        String name = user != null ? user.getUsername() : "system";
        workshopReturnService.confirm(returnNo, name);
        return ApiResult.ok("退库确认成功", null);
    }
}
