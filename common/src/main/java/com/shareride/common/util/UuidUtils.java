package com.shareride.common.util;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise utility for generating and operating on Universally Unique Identifiers (UUIDs).
 *
 * <p>Serves as the Single Source of Truth for UUID lifecycle across all ShareRide microservices,
 * abstracting third-party libraries (e.g., {@code uuid-creator}) and standardizing usage:
 *
 * <ul>
 *   <li><b>UUIDv7 (RFC 9562):</b> Recommended default for primary keys (PostgreSQL 17),
 *       transactional outbox events, and domain aggregate identifiers. Time-ordered milliseconds
 *       epoch prevents B-tree index fragmentation and maximizes insert performance.
 *   <li><b>UUIDv4 (RFC 4122/9562):</b> Cryptographically pseudo-random UUIDs for unpredictability,
 *       such as security tokens, CSRF tokens, or ephemeral verification codes.
 *   <li><b>UUIDv5 (RFC 4122/9562):</b> Name-based SHA-1 deterministic UUIDs for idempotency keys
 *       and stable cross-system data reconciliation.
 * </ul>
 */
public final class UuidUtils {

    /** RFC 4122 predefined namespace for Fully Qualified Domain Names (FQDN). */
    public static final UUID NAMESPACE_DNS =
            UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    /** RFC 4122 predefined namespace for Uniform Resource Locators (URLs). */
    public static final UUID NAMESPACE_URL =
            UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");

    /** RFC 4122 predefined namespace for ISO OIDs. */
    public static final UUID NAMESPACE_OID =
            UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8");

    /** RFC 4122 predefined namespace for X.500 Distinguished Names. */
    public static final UUID NAMESPACE_X500 =
            UUID.fromString("6ba7b814-9dad-11d1-80b4-00c04fd430c8");

    private UuidUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Generates a time-ordered UUIDv7 (RFC 9562) with unix epoch millisecond timestamp.
     *
     * <p>Primary choice for database primary keys, providing sequential ordering and optimal
     * clustering in PostgreSQL indexes.
     *
     * @return a new time-ordered UUIDv7 instance
     */
    public static UUID v7() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * Generates a time-ordered UUIDv7 string.
     *
     * @return standard canonical 36-character string representation of a new UUIDv7
     */
    public static String v7AsString() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }

    /**
     * Generates a cryptographically random UUIDv4.
     *
     * @return a new random UUIDv4 instance
     */
    public static UUID v4() {
        return UUID.randomUUID();
    }

    /**
     * Generates a cryptographically random UUIDv4 string.
     *
     * @return standard canonical 36-character string representation of a new UUIDv4
     */
    public static String v4AsString() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a deterministic name-based UUIDv5 (SHA-1 hash) using a given namespace and name.
     *
     * @param namespace the UUID namespace (e.g., {@link #NAMESPACE_DNS}, {@link #NAMESPACE_URL})
     * @param name the input string name to hash
     * @return deterministic UUIDv5 instance
     * @throws NullPointerException if namespace or name is null
     */
    public static UUID v5(UUID namespace, String name) {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(name, "name must not be null");
        return UuidCreator.getNameBasedSha1(namespace, name);
    }

    /**
     * Generates a deterministic name-based UUIDv5 string.
     *
     * @param namespace the UUID namespace
     * @param name the input string name to hash
     * @return standard canonical 36-character string representation of the UUIDv5
     */
    public static String v5AsString(UUID namespace, String name) {
        return v5(namespace, name).toString();
    }

    /**
     * Extracts the generation timestamp as an {@link Instant} from a UUIDv7.
     *
     * <p>According to RFC 9562, the first 48 bits of a UUIDv7 represent the Unix epoch timestamp in
     * milliseconds.
     *
     * @param uuid the UUIDv7 instance
     * @return {@link Optional} containing the {@link Instant} of creation, or empty if null or not
     *     version 7
     */
    public static Optional<Instant> extractInstant(UUID uuid) {
        if (!isV7(uuid)) {
            return Optional.empty();
        }
        long epochMilli = uuid.getMostSignificantBits() >>> 16;
        return Optional.of(Instant.ofEpochMilli(epochMilli));
    }

    /**
     * Extracts the Unix epoch millisecond timestamp from a UUIDv7.
     *
     * @param uuid the UUIDv7 instance
     * @return milliseconds since Unix epoch
     * @throws IllegalArgumentException if the uuid is null or not version 7
     */
    public static long extractEpochMilli(UUID uuid) {
        if (!isV7(uuid)) {
            throw new IllegalArgumentException(
                    "Cannot extract epoch milliseconds from non-UUIDv7: " + uuid);
        }
        return uuid.getMostSignificantBits() >>> 16;
    }

    /**
     * Checks if the given UUID is non-null and corresponds to version 7 (time-ordered).
     *
     * @param uuid the UUID to check
     * @return {@code true} if non-null and version 7; {@code false} otherwise
     */
    public static boolean isV7(UUID uuid) {
        return uuid != null && uuid.version() == 7;
    }

    /**
     * Checks if the given UUID is non-null and corresponds to version 4 (random-based).
     *
     * @param uuid the UUID to check
     * @return {@code true} if non-null and version 4; {@code false} otherwise
     */
    public static boolean isV4(UUID uuid) {
        return uuid != null && uuid.version() == 4;
    }

    /**
     * Checks if the given UUID is non-null and corresponds to version 5 (name-based SHA-1).
     *
     * @param uuid the UUID to check
     * @return {@code true} if non-null and version 5; {@code false} otherwise
     */
    public static boolean isV5(UUID uuid) {
        return uuid != null && uuid.version() == 5;
    }

    /**
     * Safely parses a canonical UUID string into a {@link UUID} instance.
     *
     * @param uuidString canonical 36-character string or null/blank
     * @return {@link UUID} instance, or {@code null} if the input is null or blank
     * @throws IllegalArgumentException if the string is non-blank but violates the standard UUID
     *     format
     */
    public static UUID fromString(String uuidString) {
        if (uuidString == null || uuidString.isBlank()) {
            return null;
        }
        return UUID.fromString(uuidString.trim());
    }

    /**
     * Validates whether a given string adheres to standard canonical 36-character UUID format.
     *
     * @param uuidString string to validate
     * @return {@code true} if valid; {@code false} otherwise
     */
    public static boolean isValid(String uuidString) {
        if (uuidString == null || uuidString.length() != 36) {
            return false;
        }
        try {
            UUID parsed = UUID.fromString(uuidString);
            return parsed.toString().equalsIgnoreCase(uuidString);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
