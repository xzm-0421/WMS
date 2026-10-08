package com.wms.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private Map<String, Object> userInfo;
    /** 密码是否已超过有效期（仅提醒，不阻断登录） */
    private Boolean passwordExpired;
}
