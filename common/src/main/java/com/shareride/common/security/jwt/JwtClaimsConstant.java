package com.shareride.common.security.jwt;

import java.time.Duration;

/**
 * Enterprise standard claims and JOSE header constants compliant with RFC 7519 (JWT), RFC 9068
 * (OAuth 2.0 Access Token Profile), and RFC 8037 (CFRG Elliptic Curve Signatures - EdDSA /
 * Ed25519).
 */
public final class JwtClaimsConstant {

    private JwtClaimsConstant() {
        // Utility class: prevent instantiation
    }

    /** RFC 7519: JWT ID (Unique, unguessable identifier for the token, used for revocation). */
    public static final String CLAIM_JTI = "jti";

    /** RFC 7519: Issuer (Identity provider that issued the JWT). */
    public static final String CLAIM_ISS = "iss";

    /** RFC 7519: Subject (Principal identity, typically Account/User UUID). */
    public static final String CLAIM_SUB = "sub";

    /** RFC 7519: Audience (Intended recipients/services of the token). */
    public static final String CLAIM_AUD = "aud";

    /** RFC 7519: Expiration Time (Unix timestamp in seconds when token expires). */
    public static final String CLAIM_EXP = "exp";

    /** RFC 7519: Not Before (Unix timestamp in seconds before which token must not be accepted). */
    public static final String CLAIM_NBF = "nbf";

    /** RFC 7519: Issued At (Unix timestamp in seconds when token was created). */
    public static final String CLAIM_IAT = "iat";

    /** Enterprise Claim: User email address for auditing and display. */
    public static final String CLAIM_EMAIL = "email";

    /** Enterprise Claim: List of assigned RBAC role codes (e.g. ROLE_RIDER, ROLE_DRIVER). */
    public static final String CLAIM_ROLES = "roles";

    /**
     * Enterprise Claim: List of fine-grained granular permissions (e.g. trip:create, trip:read).
     */
    public static final String CLAIM_PERMISSIONS = "permissions";

    /** Enterprise Claim: Session ID (UUID) tying token to device login session. */
    public static final String CLAIM_SESSION_ID = "sid";

    /** Enterprise Claim: Device identifier that originated the session. */
    public static final String CLAIM_DEVICE_ID = "did";

    /** Enterprise Claim: Tenant identifier for enterprise multi-tenancy isolation. */
    public static final String CLAIM_TENANT_ID = "tid";

    /** Enterprise Claim: Token schema version for backward compatibility and rotation. */
    public static final String CLAIM_VERSION = "ver";

    /** RFC 9068: JOSE Header Type for OAuth 2.0 Access Tokens. */
    public static final String HEADER_TYPE_AT_JWT = "at+jwt";

    /** RFC 7519: Standard JOSE Header Type for JWTs. */
    public static final String HEADER_TYPE_JWT = "JWT";

    /** RFC 8037: Signature algorithm name for Edwards-curve Digital Signature Algorithm. */
    public static final String ALGORITHM_EDDSA = "EdDSA";

    /** RFC 8037: Curve name for Ed25519. */
    public static final String CURVE_ED25519 = "Ed25519";

    /** Default token schema version. */
    public static final String DEFAULT_TOKEN_VERSION = "1.0";

    /** Default issuer identifier if unspecified. */
    public static final String DEFAULT_ISSUER = "shareride-auth";

    /** Default primary audience if unspecified. */
    public static final String DEFAULT_AUDIENCE = "shareride-platform";

    /** Default clock skew tolerance (60 seconds) for network drift between distributed nodes. */
    public static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(60);
}
