package com.shareride.common.versioning;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/** Strategy interface to resolve the requested API version from an HTTP request. */
public interface ApiVersionResolver {

    /**
     * Resolve API version from the request (e.g. URI path, Header, Query param, Accept header).
     *
     * @param request HTTP request
     * @return normalized resolved version, or empty if not specified
     */
    Optional<String> resolveVersion(HttpServletRequest request);
}
