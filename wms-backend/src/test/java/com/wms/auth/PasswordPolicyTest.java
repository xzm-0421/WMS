package com.wms.auth;

import com.wms.auth.domain.PasswordPolicy;
import com.wms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void rejectsTooShort() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("Ab1!", "user"));
    }

    @Test
    void rejectsTooFewCharacterClasses() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("abcdefgh", "user"));
    }

    @Test
    void rejectsUsernameAsPassword() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("Admin@123", "Admin@123"));
    }

    @Test
    void acceptsStrongPassword() {
        PasswordPolicy.validate("Abc@12345", "user");
    }

    @Test
    void expiryRule() {
        assertFalse(PasswordPolicy.isExpired(LocalDateTime.now().minusDays(10), 90));
        assertTrue(PasswordPolicy.isExpired(LocalDateTime.now().minusDays(91), 90));
        assertFalse(PasswordPolicy.isExpired(null, 90));
        assertFalse(PasswordPolicy.isExpired(LocalDateTime.now().minusDays(100), 0));
    }
}
