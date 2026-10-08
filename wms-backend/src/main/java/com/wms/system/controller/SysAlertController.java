package com.wms.system.controller;

import com.wms.auth.security.LoginUser;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.system.entity.SysAlert;
import com.wms.system.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统管理-告警")
@RestController
@RequestMapping("/system/alerts")
@RequiredArgsConstructor
public class SysAlertController {

    private final AlertService alertService;

    @Operation(summary = "告警列表")
    @GetMapping
    public ApiResult<PageResult<SysAlert>> list(
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(alertService.page(alertType, status, current, size));
    }

    @Operation(summary = "确认告警")
    @PostMapping("/{id}/ack")
    public ApiResult<Void> ack(@PathVariable Long id) {
        alertService.ack(id, operator());
        return ApiResult.ok("已确认", null);
    }

    @Operation(summary = "关闭告警")
    @PostMapping("/{id}/close")
    public ApiResult<Void> close(@PathVariable Long id) {
        alertService.close(id, operator());
        return ApiResult.ok("已关闭", null);
    }

    private String operator() {
        LoginUser user = SecurityUtils.currentUser();
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }
}
