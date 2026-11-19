package com.shareride.auth.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HashingUtilsTest {

    @Test
    void sha256Hex_ShouldReturn64CharacterLowercaseHexString() {
        final String input = "K8M2P4";
        final String hash = HashingUtils.sha256Hex(input);

        assertEquals(64, hash.length(), "SHA-256 hash should be 64 characters");
        assertTrue(hash.matches("^[a-f0-9]{64}$"), "Hash should be lowercase hex");
    }

    @Test
    void sha256Hex_ShouldProduceSameHashForUppercaseAndLowercaseInput() {
        final String lowercase = "k8m2p4";
        final String uppercase = "K8M2P4";
        final String mixed = "K8m2P4";

        final String hash1 = HashingUtils.sha256Hex(lowercase);
        final String hash2 = HashingUtils.sha256Hex(uppercase);
        final String hash3 = HashingUtils.sha256Hex(mixed);

        assertEquals(hash1, hash2, "Lowercase and uppercase should produce same hash");
        assertEquals(hash2, hash3, "Mixed case should produce same hash");
    }

    @Test
    void sha256Hex_ShouldHandleLeadingAndTrailingWhitespace() {
        final String withWhitespace = "  K8M2P4  ";
        final String withoutWhitespace = "K8M2P4";

        final String hash1 = HashingUtils.sha256Hex(withWhitespace);
        final String hash2 = HashingUtils.sha256Hex(withoutWhitespace);

        assertEquals(hash1, hash2, "Whitespace should be trimmed before hashing");
    }

    @Test
    void sha256Hex_ShouldProduceDeterministicOutput() {
        final String input = "ABC123";

        final String hash1 = HashingUtils.sha256Hex(input);
        final String hash2 = HashingUtils.sha256Hex(input);

        assertEquals(hash1, hash2, "Same input should always produce same hash");
    }

    @Test
    void constantTimeEquals_ShouldReturnTrueForIdenticalHashes() {
        final String hash1 = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";
        final String hash2 = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";

        assertTrue(HashingUtils.constantTimeEquals(hash1, hash2), "Identical hashes should match");
    }

    @Test
    void constantTimeEquals_ShouldReturnFalseForDifferentHashes() {
        final String hash1 = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";
        final String hash2 = "6dcd4ce23d88e2ee9568ba546c007c63d9131c1b948e0b4e17e7a0b0e7b1e3c2";

        assertFalse(
                HashingUtils.constantTimeEquals(hash1, hash2), "Different hashes should not match");
    }

    @Test
    void constantTimeEquals_ShouldReturnFalseForNullInputs() {
        final String hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";

        assertFalse(HashingUtils.constantTimeEquals(null, hash));
        assertFalse(HashingUtils.constantTimeEquals(hash, null));
        assertFalse(HashingUtils.constantTimeEquals(null, null));
    }

    @Test
    void constantTimeEquals_ShouldUseConstantTimeComparison() {
        final String hash1 = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";
        final String hash2Prefix = "5e884898";
        final String hash2Suffix = "da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";

        assertFalse(HashingUtils.constantTimeEquals(hash1, hash2Prefix));
        assertFalse(HashingUtils.constantTimeEquals(hash1, hash2Suffix));
    }
}
