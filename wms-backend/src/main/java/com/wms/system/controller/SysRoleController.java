package com.wms.system.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.system.dto.RolePermissionsRequest;
import com.wms.system.dto.SysRoleDto;
import com.wms.system.entity.SysRole;
import com.wms.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统管理-角色")
@RestController
@RequestMapping("/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "角色列表")
    @GetMapping
    public ApiResult<PageResult<SysRoleDto>> list(
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(roleService.page(roleCode, roleName, status, current, size));
    }

    @Operation(summary = "角色详情")
    @GetMapping("/{id}")
    public ApiResult<SysRoleDto> detail(@PathVariable Long id) {
        return ApiResult.ok(roleService.getById(id));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    public ApiResult<Void> create(@RequestBody SysRole role) {
        roleService.create(role);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        roleService.update(id, role);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResult.ok("删除成功", null);
    }

    @Operation(summary = "分配权限")
    @PutMapping("/{id}/permissions")
    public ApiResult<Void> updatePermissions(@PathVariable Long id,
                                             @RequestBody RolePermissionsRequest request) {
        roleService.updatePermissions(id, request);
        return ApiResult.ok("权限更新成功", null);
    }
}
