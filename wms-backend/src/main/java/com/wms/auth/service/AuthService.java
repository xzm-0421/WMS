package com.wms.auth.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.wms.auth.dto.*;
import com.wms.auth.security.JwtTokenProvider;
import com.wms.auth.security.LoginUser;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.system.entity.SysUser;
import com.wms.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CAPTCHA_PREFIX = "wms:captcha:";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final Map<String, String> memoryCaptcha = new ConcurrentHashMap<>();

    public Map<String, String> generateCaptcha() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String key = UUID.randomUUID().toString(true);
        storeCaptcha(key, captcha.getCode().toLowerCase());
        return Map.of(
                "captchaKey", key,
                "captchaImage", captcha.getImageBase64Data()
        );
    }

    public LoginResponse login(LoginRequest request) {
        validateCaptcha(request.getCaptchaKey(), request.getCaptcha());
        return doLogin(request.getUsername(), request.getPassword(), false, null);
    }

    public LoginResponse mobileLogin(MobileLoginRequest request) {
        LoginResponse response = doLogin(request.getUsername(), request.getPassword(), true, request.getDeviceNo());
        return response;
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        var claims = jwtTokenProvider.parseToken(request.getRefreshToken());
        if (!"refresh".equals(claims.get("type"))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token无效", "TOKEN_INVALID");
        }
        Long userId = claims.get("userId", Long.class);
        String username = claims.getSubject();
        return LoginResponse.builder()
                .accessToken(jwtTokenProvider.createAccessToken(userId, username))
                .refreshToken(jwtTokenProvider.createRefreshToken(userId, username))
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpire())
                .build();
    }

    public Map<String, Object> getUserInfo() {
        LoginUser user = currentUser();
        Map<String, Object> info = new HashMap<>();
        info.put("userId", String.valueOf(user.getUserId()));
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("roles", user.getRoles());
        info.put("permissions", user.getPermissions());
        info.put("warehouseScope", user.getWarehouseScope());
        info.put("dataScope", user.getDataScope());
        info.put("isSuperAdmin", user.isSuperAdmin());
        return info;
    }

    public void changePassword(ChangePasswordRequest request) {
        LoginUser user = currentUser();
        SysUser sysUser = userMapper.selectById(user.getUserId());
        if (!passwordEncoder.matches(sha256Hex(request.getOldPassword()), sysUser.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原密码错误");
        }
        SysUser update = new SysUser();
        update.setId(user.getUserId());
        update.setPassword(passwordEncoder.encode(sha256Hex(request.getNewPassword())));
        userMapper.updateById(update);
    }

    private LoginResponse doLogin(String username, String password, boolean mobile, String deviceNo) {
        // 客户端已 SHA256 传输，库中存 BCrypt(SHA256(明文))，此处不再二次哈希
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        LoginUser user = (LoginUser) authentication.getPrincipal();
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", String.valueOf(user.getUserId()));
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("roles", user.getRoles());
        if (mobile) {
            List<String> scope = user.getWarehouseScope();
            userInfo.put("warehouseCode", scope == null || scope.isEmpty() ? null : scope.get(0));
            userInfo.put("warehouseName", null);
        } else {
            userInfo.put("permissions", user.getPermissions());
            userInfo.put("warehouseScope", user.getWarehouseScope());
            userInfo.put("dataScope", user.getDataScope());
            userInfo.put("isSuperAdmin", user.isSuperAdmin());
        }
        return LoginResponse.builder()
                .accessToken(jwtTokenProvider.createAccessToken(user.getUserId(), user.getUsername()))
                .refreshToken(jwtTokenProvider.createRefreshToken(user.getUserId(), user.getUsername()))
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpire())
                .userInfo(userInfo)
                .build();
    }

    private void validateCaptcha(String key, String code) {
        if (StrUtil.isBlank(key) || StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码不能为空");
        }
        String stored = getCaptcha(key);
        if (stored == null || !stored.equalsIgnoreCase(code.trim())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误");
        }
        removeCaptcha(key);
    }

    private void storeCaptcha(String key, String code) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(CAPTCHA_PREFIX + key, code, Duration.ofMinutes(5));
        } else {
            memoryCaptcha.put(key, code);
        }
    }

    private String getCaptcha(String key) {
        if (redisTemplate != null) {
            return redisTemplate.opsForValue().get(CAPTCHA_PREFIX + key);
        }
        return memoryCaptcha.get(key);
    }

    private void removeCaptcha(String key) {
        if (redisTemplate != null) {
            redisTemplate.delete(CAPTCHA_PREFIX + key);
        } else {
            memoryCaptcha.remove(key);
        }
    }

    private LoginUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return loginUser;
    }

    /** 前端 SHA256 后传输，此处与库中 BCrypt(SHA256(plain)) 比对 */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
