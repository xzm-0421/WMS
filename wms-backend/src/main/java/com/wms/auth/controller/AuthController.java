package com.wms.auth.controller;

import com.wms.auth.dto.*;
import com.wms.auth.service.AuthService;
import com.wms.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "认证模块")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Web后台登录")
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok("登录成功", authService.login(request));
    }

    @Operation(summary = "PDA移动端登录")
    @PostMapping("/mobile/login")
    public ApiResult<LoginResponse> mobileLogin(@Valid @RequestBody MobileLoginRequest request) {
        return ApiResult.ok("登录成功", authService.mobileLogin(request));
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public ApiResult<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResult.ok("刷新成功", authService.refresh(request));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        return ApiResult.ok("登出成功", null);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/userinfo")
    public ApiResult<Map<String, Object>> userInfo() {
        return ApiResult.ok(authService.getUserInfo());
    }

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public ApiResult<Map<String, String>> captcha() {
        return ApiResult.ok(authService.generateCaptcha());
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResult.ok("密码修改成功", null);
    }
}
