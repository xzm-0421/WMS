package com.wms.common.security;

import com.wms.auth.security.LoginUser;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return loginUser;
    }

    public static boolean isSuperAdmin() {
        LoginUser user = currentUser();
        return user.isSuperAdmin()
                || (user.getRoles() != null && user.getRoles().contains(SUPER_ADMIN_ROLE));
    }

    public static void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅超级管理员可配置数据范围与功能权限");
        }
    }
}
