package com.shareride.auth.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.auth.constant.AuthVerificationConstants;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlphanumericCodeGeneratorTest {

    @Test
    void generate_ShouldReturnExactSixCharacters() {
        final String code = AlphanumericCodeGenerator.generate();
        assertEquals(AuthVerificationConstants.OTP_LENGTH, code.length());
    }

    @Test
    void generate_ShouldContainOnlyUnambiguousCharacters() {
        final String code = AlphanumericCodeGenerator.generate();
        final String allowedChars = AuthVerificationConstants.UNAMBIGUOUS_CHARS;

        for (char c : code.toCharArray()) {
            assertTrue(allowedChars.indexOf(c) >= 0, "Code contains disallowed character: " + c);
        }
    }

    @Test
    void generate_ShouldNotContainAmbiguousCharacters() {
        final String ambiguousChars = "01OI";

        for (int i = 0; i < 100; i++) {
            final String code = AlphanumericCodeGenerator.generate();
            for (char ambiguous : ambiguousChars.toCharArray()) {
                assertFalse(
                        code.indexOf(ambiguous) >= 0,
                        "Code contains ambiguous character: " + ambiguous);
            }
        }
    }

    @Test
    void generate_ShouldProduceHighEntropyOver10000Iterations() {
        final Set<String> uniqueCodes = new HashSet<>();
        final int iterations = 10000;

        for (int i = 0; i < iterations; i++) {
            uniqueCodes.add(AlphanumericCodeGenerator.generate());
        }

        final double uniquenessRatio = (double) uniqueCodes.size() / iterations;
        assertTrue(
                uniquenessRatio > 0.99, "Entropy is too low. Uniqueness ratio: " + uniquenessRatio);
    }

    @Test
    void generate_ShouldProduceDifferentCodesOnConsecutiveCalls() {
        final String code1 = AlphanumericCodeGenerator.generate();
        final String code2 = AlphanumericCodeGenerator.generate();
        final String code3 = AlphanumericCodeGenerator.generate();

        assertNotEquals(code1, code2);
        assertNotEquals(code2, code3);
        assertNotEquals(code1, code3);
    }
}
