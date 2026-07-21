package com.wms.system.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.security.SecurityUtils;
import com.wms.system.dto.PermissionModuleGroup;
import com.wms.system.service.SysPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "系统管理-权限")
@RestController
@RequestMapping("/system/permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService permissionService;

    @Operation(summary = "权限列表（按模块分组）")
    @GetMapping
    public ApiResult<List<PermissionModuleGroup>> list() {
        SecurityUtils.requireSuperAdmin();
        return ApiResult.ok(permissionService.listGrouped());
    }
}
