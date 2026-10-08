package com.wms.auth.domain;

import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 密码强度与有效期策略：长度≥8、至少 3 类字符、不得与用户名相同；90 天过期（仅提示，不强制）。
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    /** 校验明文密码强度（在 SHA256/BCrypt 之前调用）。 */
    public static void validate(String rawPassword, String username) {
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < MIN_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度不能少于 " + MIN_LENGTH + " 位");
        }
        int classes = 0;
        if (rawPassword.chars().anyMatch(Character::isUpperCase)) {
            classes++;
        }
        if (rawPassword.chars().anyMatch(Character::isLowerCase)) {
            classes++;
        }
        if (rawPassword.chars().anyMatch(Character::isDigit)) {
            classes++;
        }
        if (rawPassword.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            classes++;
        }
        if (classes < 3) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "密码需包含大写字母、小写字母、数字、特殊字符中的至少 3 类");
        }
        if (StringUtils.hasText(username) && rawPassword.equalsIgnoreCase(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能与用户名相同");
        }
    }

    /** 是否已过期；expireDays<=0 或从未修改过则不判定为过期。 */
    public static boolean isExpired(LocalDateTime pwdUpdatedAt, int expireDays) {
        if (expireDays <= 0 || pwdUpdatedAt == null) {
            return false;
        }
        return pwdUpdatedAt.plusDays(expireDays).isBefore(LocalDateTime.now());
    }
}
