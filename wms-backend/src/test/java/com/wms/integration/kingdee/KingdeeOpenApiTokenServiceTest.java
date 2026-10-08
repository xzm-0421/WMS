package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdeeOpenApiTokenServiceTest {

    private KingdeeOpenApiTokenService newService(KingdeeCloudProperties props) {
        return new KingdeeOpenApiTokenService(props, new ObjectMapper());
    }

    @Test
    void parseTokenResponse_extractsTokenAndExpiry() {
        KingdeeOpenApiTokenService service = newService(new KingdeeCloudProperties());
        String json = "{\"data\":{\"access_token\":\"v1|abc\",\"token_type\":\"Bearer\","
                + "\"refresh_token\":\"r1\",\"expires_in\":7200000,\"id_token\":\"jwt\"},"
                + "\"errorCode\":\"0\",\"message\":\"success\",\"status\":true}";
        KingdeeOpenApiTokenService.TokenInfo info = service.parseTokenResponse(json);
        assertEquals("v1|abc", info.accessToken());
        assertEquals("r1", info.refreshToken());
        assertEquals(7200000L, info.expiresInMs());
        assertEquals("jwt", info.idToken());
    }

    @Test
    void parseTokenResponse_errorCode_throws() {
        KingdeeOpenApiTokenService service = newService(new KingdeeCloudProperties());
        String json = "{\"errorCode\":\"401\",\"message\":\"bad secret\",\"status\":false}";
        BusinessException ex = assertThrows(BusinessException.class, () -> service.parseTokenResponse(json));
        assertTrue(ex.getMessage().contains("bad secret"));
        assertEquals("KINGDEE_OAUTH_ERROR", ex.getErrorType());
    }

    @Test
    void getValidToken_withoutCredentials_throws() {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        props.setAppId("");
        props.setAppSecret("");
        KingdeeOpenApiTokenService service = newService(props);
        BusinessException ex = assertThrows(BusinessException.class, service::getValidToken);
        assertEquals("KINGDEE_OAUTH_NOT_CONFIGURED", ex.getErrorType());
    }
}
