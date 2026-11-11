package com.shareride.auth.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordUtilsTest {

    @Test
    @DisplayName("PasswordUtils: Should hash raw password using Argon2id with secret pepper")
    void testHashPasswordWithSecret() {
        String rawPassword = "StrongPassword@2026";
        String customSecret = "custom_secret_pepper_999";

        String hash = PasswordUtils.hashPassword(rawPassword, customSecret);

        assertNotNull(hash);
        assertTrue(
                hash.startsWith("$argon2id$v=19$m=16384,t=2,p=1$"),
                "Hash must follow standard Argon2id format");

        assertTrue(
                PasswordUtils.verifyPassword(rawPassword, hash, customSecret),
                "Password must match when correct password and secret are supplied");

        assertFalse(
                PasswordUtils.verifyPassword("WrongPassword", hash, customSecret),
                "Password must not match when wrong password is supplied");

        assertFalse(
                PasswordUtils.verifyPassword(rawPassword, hash, "different_secret"),
                "Verification must fail when a different secret pepper is used");
    }

    @Test
    @DisplayName("PasswordUtils: Should correctly use default configured secret")
    void testHashAndVerifyWithConfiguredSecret() {
        String rawPassword = "UserSecurePassword!77";
        String hash = PasswordUtils.hashPassword(rawPassword);

        assertTrue(PasswordUtils.verifyPassword(rawPassword, hash));
        assertFalse(PasswordUtils.verifyPassword("IncorrectPass", hash));
    }

    @Test
    @DisplayName("PasswordUtils: Should validate null or empty inputs appropriately")
    void testInputValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PasswordUtils.hashPassword(null),
                "Hashing null raw password must throw IllegalArgumentException");

        assertFalse(PasswordUtils.verifyPassword(null, "$argon2id$..."));
        assertFalse(PasswordUtils.verifyPassword("pass", null));
        assertFalse(PasswordUtils.verifyPassword("pass", "   "));
    }

    @Test
    @DisplayName("PasswordUtils: Should support getEncoder() Spring PasswordEncoder interface")
    void testPasswordEncoderInterface() {
        org.springframework.security.crypto.password.PasswordEncoder encoder =
                PasswordUtils.getEncoder();
        assertNotNull(encoder);

        String raw = "SpringEncoderPassword123";
        String encoded = encoder.encode(raw);
        assertNotNull(encoded);
        assertTrue(encoder.matches(raw, encoded));
        assertFalse(encoder.matches("wrong", encoded));
        assertFalse(encoder.matches(null, encoded));
        assertFalse(encoder.upgradeEncoding(encoded));
    }

    @Test
    @DisplayName("PasswordUtils: When secret is null or blank, falls back to raw password")
    void testBlankSecretFallback() {
        String raw = "PasswordWithoutSecret";
        String hashWithNullSecret = PasswordUtils.hashPassword(raw, null);
        assertTrue(PasswordUtils.verifyPassword(raw, hashWithNullSecret, null));

        String hashWithBlankSecret = PasswordUtils.hashPassword(raw, "   ");
        assertTrue(PasswordUtils.verifyPassword(raw, hashWithBlankSecret, "   "));
    }

    @Test
    @DisplayName("PasswordUtils: setSecret updates global secret and null falls back")
    void testSetSecretAndFallback() {
        PasswordUtils.setSecret("temporary_secret_pepper_456");
        org.junit.jupiter.api.Assertions.assertEquals(
                "temporary_secret_pepper_456", PasswordUtils.getSecret());

        PasswordUtils.setSecret(null);
        assertNotNull(PasswordUtils.getSecret());
    }
}
