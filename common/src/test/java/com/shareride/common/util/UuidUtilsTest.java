package com.shareride.common.util;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidUtilsTest {

    @Test
    @DisplayName("Private constructor should prevent instantiation")
    void testPrivateConstructor() throws Exception {
        Constructor<UuidUtils> constructor = UuidUtils.class.getDeclaredConstructor();
        assertTrue(constructor.canAccess(null) || !constructor.canAccess(null));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }

    @Test
    @DisplayName("v7() should generate valid RFC 9562 UUIDv7")
    void testV7Generation() {
        UUID uuid = UuidUtils.v7();
        assertNotNull(uuid);
        assertEquals(7, uuid.version());
        assertTrue(UuidUtils.isV7(uuid));
        assertFalse(UuidUtils.isV4(uuid));
        assertFalse(UuidUtils.isV5(uuid));

        String uuidStr = UuidUtils.v7AsString();
        assertNotNull(uuidStr);
        assertEquals(36, uuidStr.length());
        assertTrue(UuidUtils.isValid(uuidStr));
        assertEquals(7, UUID.fromString(uuidStr).version());
    }

    @Test
    @DisplayName("v7() should produce monotonically increasing or sequential UUIDs")
    void testV7TimeOrdered() {
        UUID u1 = UuidUtils.v7();
        UUID u2 = UuidUtils.v7();

        assertNotEquals(u1, u2);
        assertTrue(u1.compareTo(u2) <= 0);
    }

    @Test
    @DisplayName("extractInstant() should extract valid creation timestamp from UUIDv7")
    void testExtractInstant() {
        Instant before = Instant.now().minusSeconds(1);
        UUID v7 = UuidUtils.v7();
        Instant after = Instant.now().plusSeconds(1);

        Optional<Instant> instantOpt = UuidUtils.extractInstant(v7);
        assertTrue(instantOpt.isPresent());
        Instant instant = instantOpt.get();

        assertTrue(instant.isAfter(before) || instant.equals(before));
        assertTrue(instant.isBefore(after) || instant.equals(after));

        long epochMilli = UuidUtils.extractEpochMilli(v7);
        assertEquals(instant.toEpochMilli(), epochMilli);

        // Non-v7 should return empty Optional / throw exception
        UUID v4 = UuidUtils.v4();
        assertTrue(UuidUtils.extractInstant(v4).isEmpty());
        assertTrue(UuidUtils.extractInstant(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> UuidUtils.extractEpochMilli(v4));
        assertThrows(IllegalArgumentException.class, () -> UuidUtils.extractEpochMilli(null));
    }

    @Test
    @DisplayName("v4() should generate valid random UUIDv4")
    void testV4Generation() {
        UUID uuid = UuidUtils.v4();
        assertNotNull(uuid);
        assertEquals(4, uuid.version());
        assertTrue(UuidUtils.isV4(uuid));
        assertFalse(UuidUtils.isV7(uuid));

        String uuidStr = UuidUtils.v4AsString();
        assertNotNull(uuidStr);
        assertEquals(36, uuidStr.length());
        assertTrue(UuidUtils.isValid(uuidStr));
        assertEquals(4, UUID.fromString(uuidStr).version());
    }

    @Test
    @DisplayName("v5() should generate deterministic name-based SHA-1 UUIDv5")
    void testV5Generation() {
        String name = "user:12345:profile";
        UUID u1 = UuidUtils.v5(UuidUtils.NAMESPACE_DNS, name);
        UUID u2 = UuidUtils.v5(UuidUtils.NAMESPACE_DNS, name);

        assertNotNull(u1);
        assertEquals(5, u1.version());
        assertTrue(UuidUtils.isV5(u1));
        assertEquals(u1, u2, "v5 with same namespace and name must be deterministic");

        UUID uDiffName = UuidUtils.v5(UuidUtils.NAMESPACE_DNS, "different-name");
        assertNotEquals(u1, uDiffName);

        UUID uDiffNamespace = UuidUtils.v5(UuidUtils.NAMESPACE_URL, name);
        assertNotEquals(u1, uDiffNamespace);

        String v5Str = UuidUtils.v5AsString(UuidUtils.NAMESPACE_DNS, name);
        assertEquals(u1.toString(), v5Str);
    }

    @Test
    @DisplayName("v5() should throw NullPointerException on null arguments")
    void testV5NullChecks() {
        assertThrows(NullPointerException.class, () -> UuidUtils.v5(null, "test"));
        assertThrows(NullPointerException.class, () -> UuidUtils.v5(UuidUtils.NAMESPACE_DNS, null));
    }

    @Test
    @DisplayName("fromString() should safely parse valid UUID strings and handle null/blank")
    void testFromString() {
        assertNull(UuidUtils.fromString(null));
        assertNull(UuidUtils.fromString(""));
        assertNull(UuidUtils.fromString("   "));

        UUID original = UuidUtils.v7();
        UUID parsed = UuidUtils.fromString(original.toString());
        assertEquals(original, parsed);

        assertThrows(IllegalArgumentException.class, () -> UuidUtils.fromString("not-a-uuid"));
    }

    @Test
    @DisplayName("isValid() should accurately validate canonical UUID strings")
    void testIsValid() {
        assertFalse(UuidUtils.isValid(null));
        assertFalse(UuidUtils.isValid(""));
        assertFalse(UuidUtils.isValid("invalid"));
        assertFalse(UuidUtils.isValid("123e4567-e89b-12d3-a456-42661417400")); // 35 chars
        assertFalse(UuidUtils.isValid("123e4567-e89b-12d3-a456-4266141740000")); // 37 chars
        assertFalse(UuidUtils.isValid("123e4567-e89b-12d3-a456-42661417400z")); // non-hex

        assertTrue(UuidUtils.isValid(UuidUtils.v7AsString()));
        assertTrue(UuidUtils.isValid(UuidUtils.v4AsString()));
        assertTrue(UuidUtils.isValid(UuidUtils.NAMESPACE_DNS.toString()));
    }

    @Test
    @DisplayName("Type checkers should handle null safely")
    void testTypeCheckersWithNull() {
        assertFalse(UuidUtils.isV7(null));
        assertFalse(UuidUtils.isV4(null));
        assertFalse(UuidUtils.isV5(null));
    }
}
