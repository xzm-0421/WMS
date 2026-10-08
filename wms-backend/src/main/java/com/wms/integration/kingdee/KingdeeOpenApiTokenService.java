package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 金蝶 AI 苍穹 OpenAPI（kapi）OAuth2 令牌管理。
 * <p>
 * 获取：POST {base-url}/kapi/oauth2/getToken
 * 校验：POST {base-url}/kapi/oauth2/verifyToken
 * 撤回：POST {base-url}/kapi/oauth2/withdrawToken
 * <p>
 * access_token 有效期 2 小时；剩余低于 {@code refresh-threshold-ms} 时自动重新获取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KingdeeOpenApiTokenService {

    private final KingdeeCloudProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private final Object lock = new Object();
    private volatile String accessToken;
    private volatile long expireAtMs;

    /** 获取可用的 access_token（带缓存与临期刷新）。 */
    public String getValidToken() {
        long now = System.currentTimeMillis();
        String cached = accessToken;
        if (StringUtils.hasText(cached) && now < expireAtMs) {
            return cached;
        }
        synchronized (lock) {
            now = System.currentTimeMillis();
            if (StringUtils.hasText(accessToken) && now < expireAtMs) {
                return accessToken;
            }
            TokenInfo info = requestToken();
            accessToken = info.accessToken();
            long ttl = info.expiresInMs() > 0 ? info.expiresInMs() : 7_200_000L;
            long threshold = Math.max(0L, properties.getRefreshThresholdMs());
            expireAtMs = System.currentTimeMillis() + Math.max(ttl - threshold, 0L);
            log.info("Kingdee OpenAPI access_token refreshed, ttlMs={}, refreshInMs={}",
                    ttl, Math.max(ttl - threshold, 0L));
            return accessToken;
        }
    }

    /** 会话失效时强制下次重新获取。 */
    public void invalidate() {
        synchronized (lock) {
            accessToken = null;
            expireAtMs = 0L;
        }
    }

    /** 是否具备 OAuth2 模式所需凭据。 */
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getAppId()) && StringUtils.hasText(properties.getAppSecret());
    }

    /** 通过 getToken 获取新令牌。 */
    public TokenInfo requestToken() {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "金蝶 OpenAPI 未配置 app-id/app-secret（请写入 application-dev-local.yml）",
                    "KINGDEE_OAUTH_NOT_CONFIGURED");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("client_id", properties.getAppId());
        body.put("client_secret", properties.getAppSecret());
        body.put("username", properties.getUsername());
        body.put("accountId", properties.getAcctId());
        body.put("language", properties.getLanguage());
        body.put("nonce", nonce());
        body.put("timestamp", timestamp());
        String responseJson = post(tokenUrl("getToken"), body);
        return parseTokenResponse(responseJson);
    }

    /** 解析 getToken 响应（独立方法便于单测）。 */
    TokenInfo parseTokenResponse(String responseJson) {
        JsonNode root = readTree(responseJson);
        String errorCode = root.path("errorCode").asText("");
        JsonNode data = root.path("data");
        if (!"0".equals(errorCode) || data.isMissingNode() || data.isNull()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "金蝶获取 access_token 失败: " + root.path("message").asText(errorCode),
                    "KINGDEE_OAUTH_ERROR");
        }
        String token = data.path("access_token").asText("");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "金蝶 getToken 返回缺少 access_token", "KINGDEE_OAUTH_ERROR");
        }
        return new TokenInfo(token,
                data.path("refresh_token").asText(""),
                data.path("expires_in").asLong(0L),
                data.path("id_token").asText(""));
    }

    /** 校验当前 access_token 的有效性（返回 active/expires_in）。 */
    public Map<String, Object> verifyCurrentToken() {
        String token = getValidToken();
        ObjectNode body = baseBody();
        body.put("token_type_hint", "access_token");
        body.put("token", token);
        JsonNode root = readTree(post(tokenUrl("verifyToken"), body));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authMode", properties.getAuthMode());
        result.put("errorCode", root.path("errorCode").asText(""));
        result.put("message", root.path("message").asText(""));
        JsonNode data = root.path("data");
        result.put("active", data.path("active").asBoolean(false));
        result.put("expiresInMs", data.path("expires_in").asLong(0L));
        return result;
    }

    /** 撤回指定 access_token。 */
    public void withdrawToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        ObjectNode body = baseBody();
        body.put("client_secret", properties.getAppSecret());
        body.put("token_type_hint", "access_token");
        body.put("token", token);
        post(tokenUrl("withdrawToken"), body);
    }

    private ObjectNode baseBody() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("client_id", properties.getAppId());
        body.put("accountId", properties.getAcctId());
        body.put("nonce", nonce());
        body.put("timestamp", timestamp());
        return body;
    }

    private String post(String url, ObjectNode body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body.toString(), headers), String.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "金蝶 OAuth2 接口调用失败: " + resp.getStatusCode(), "KINGDEE_OAUTH_ERROR");
        }
        return resp.getBody();
    }

    private JsonNode readTree(String responseJson) {
        try {
            return objectMapper.readTree(responseJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "金蝶 OAuth2 响应解析失败: " + e.getMessage(), "KINGDEE_OAUTH_ERROR");
        }
    }

    private String tokenUrl(String operation) {
        String base = properties.getBaseUrl();
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金蝶 base-url 未配置", "KINGDEE_OAUTH_ERROR");
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/kapi/oauth2/" + operation;
    }

    private static String nonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String timestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    /** getToken 返回信息。 */
    public record TokenInfo(String accessToken, String refreshToken, long expiresInMs, String idToken) {
    }
}
