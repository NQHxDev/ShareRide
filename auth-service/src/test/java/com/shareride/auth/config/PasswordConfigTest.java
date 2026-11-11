package com.shareride.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

    @Test
    void testPasswordConfigInitializationAndBean() {
        PasswordProperties properties = new PasswordProperties();
        properties.setSecret("my-custom-test-secret-pepper-999");
        assertEquals("my-custom-test-secret-pepper-999", properties.getSecret());

        PasswordConfig config = new PasswordConfig(properties);
        PasswordEncoder encoder = config.passwordEncoder();
        assertNotNull(encoder);

        PasswordConfig nullConfig = new PasswordConfig(null);
        assertNotNull(nullConfig.passwordEncoder());

        PasswordProperties blankProps = new PasswordProperties();
        blankProps.setSecret("   ");
        PasswordConfig blankConfig = new PasswordConfig(blankProps);
        assertNotNull(blankConfig.passwordEncoder());
    }
}
