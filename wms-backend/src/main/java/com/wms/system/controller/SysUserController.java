package com.wms.system.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.integration.kingdee.dto.KingdeeSecUserVo;
import com.wms.system.dto.*;
import com.wms.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统管理-用户")
@RestController
@RequestMapping("/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @Operation(summary = "用户列表")
    @GetMapping
    public ApiResult<PageResult<SysUserDto>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(userService.page(username, realName, status, current, size));
    }

    @Operation(summary = "金蝶用户列表（SEC_User，供绑定选择）")
    @GetMapping("/kingdee-users")
    public ApiResult<List<KingdeeSecUserVo>> kingdeeUsers(
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(userService.listKingdeeUsers(keyword));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public ApiResult<SysUserDto> detail(@PathVariable Long id) {
        return ApiResult.ok(userService.getById(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public ApiResult<Void> create(@RequestBody SysUserCreateRequest request) {
        userService.create(request);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody SysUserUpdateRequest request) {
        userService.update(id, request);
        return ApiResult.ok("更新成功", null);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResult.ok("删除成功", null);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/reset-password")
    public ApiResult<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ApiResult.ok("密码重置成功", null);
    }

    @Operation(summary = "更新状态")
    @PutMapping("/{id}/status")
    public ApiResult<Void> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        userService.updateStatus(id, request);
        return ApiResult.ok("状态更新成功", null);
    }
}
