package com.wms.auth;

import com.wms.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPasswordTest {

    private static final String SHA256_123456 =
            "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92";

    @Test
    void sha256MatchesCryptoJs() {
        assertEquals(SHA256_123456, AuthService.sha256Hex("123456"));
    }

    @Test
    void bcryptMatchesClientHash() {
        String encoded = new BCryptPasswordEncoder().encode(SHA256_123456);
        assertTrue(new BCryptPasswordEncoder().matches(SHA256_123456, encoded));
    }
}
