package com.shareride.common.versioning;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default multi-strategy implementation of {@link ApiVersionResolver}.
 * Resolves API versions via URI path, custom HTTP headers, query parameters,
 * and content-negotiation Accept headers with automatic normalization.
 */
public class DefaultApiVersionResolver implements ApiVersionResolver {

   private static final Pattern MEDIA_TYPE_VERSION_PATTERN = Pattern.compile(
         "(?:application/vnd\\.[a-zA-Z0-9.-]+\\.)(v?[0-9]+(?:\\.[0-9]+)?)",
         Pattern.CASE_INSENSITIVE
   );

   private static final Pattern MEDIA_TYPE_PARAM_PATTERN = Pattern.compile(
         ";\\s*version=(v?[0-9]+(?:\\.[0-9]+)?)",
         Pattern.CASE_INSENSITIVE
   );

   private final ShareRideVersioningProperties properties;
   private final Pattern uriPattern;

   public DefaultApiVersionResolver(ShareRideVersioningProperties properties) {
      this.properties = properties;
      this.uriPattern = Pattern.compile(
            properties.getUriPattern() != null ? properties.getUriPattern() : "^/api/(v[0-9]+(?:\\.[0-9]+)?)(?:/.*)?$",
            Pattern.CASE_INSENSITIVE
      );
   }

   @Override
   public Optional<String> resolveVersion(HttpServletRequest request) {
      if (request == null) {
         return Optional.empty();
      }

      // 1. Resolve from URI Path (highest specificity: identifies the exact resource endpoint)
      String pathVersion = resolveFromPath(request);
      if (pathVersion != null && !pathVersion.isBlank()) {
         return Optional.of(ShareRideVersioningProperties.normalizeVersion(pathVersion));
      }

      // 2. Resolve from Request Header (X-API-Version, API-Version, Accept-Version)
      String headerVersion = resolveFromHeader(request);
      if (headerVersion != null && !headerVersion.isBlank()) {
         return Optional.of(ShareRideVersioningProperties.normalizeVersion(headerVersion));
      }

      // 3. Resolve from Query Parameter (api-version, version)
      String queryVersion = resolveFromQuery(request);
      if (queryVersion != null && !queryVersion.isBlank()) {
         return Optional.of(ShareRideVersioningProperties.normalizeVersion(queryVersion));
      }

      // 4. Resolve from Accept Header (application/vnd.shareride.v1+json or version=v1)
      String acceptVersion = resolveFromAccept(request);
      if (acceptVersion != null && !acceptVersion.isBlank()) {
         return Optional.of(ShareRideVersioningProperties.normalizeVersion(acceptVersion));
      }

      return Optional.empty();
   }

   private String resolveFromPath(HttpServletRequest request) {
      String uri = request.getRequestURI();
      if (uri != null) {
         Matcher matcher = uriPattern.matcher(uri);
         if (matcher.find() && matcher.groupCount() >= 1) {
            return matcher.group(1);
         }
      }
      String servletPath = request.getServletPath();
      if (servletPath != null && !servletPath.equals(uri)) {
         Matcher matcher = uriPattern.matcher(servletPath);
         if (matcher.find() && matcher.groupCount() >= 1) {
            return matcher.group(1);
         }
      }
      return null;
   }

   private String resolveFromHeader(HttpServletRequest request) {
      String configuredHeader = properties.getHeaderName();
      if (configuredHeader != null && !configuredHeader.isBlank()) {
         String val = request.getHeader(configuredHeader);
         if (val != null && !val.isBlank()) {
            return val.trim();
         }
      }

      String alt1 = request.getHeader("API-Version");
      if (alt1 != null && !alt1.isBlank()) {
         return alt1.trim();
      }

      String alt2 = request.getHeader("Accept-Version");
      if (alt2 != null && !alt2.isBlank()) {
         return alt2.trim();
      }

      return null;
   }

   private String resolveFromQuery(HttpServletRequest request) {
      String configuredParam = properties.getParamName();
      if (configuredParam != null && !configuredParam.isBlank()) {
         String val = request.getParameter(configuredParam);
         if (val != null && !val.isBlank()) {
            return val.trim();
         }
      }

      String altParam = request.getParameter("version");
      if (altParam != null && !altParam.isBlank()) {
         return altParam.trim();
      }

      return null;
   }

   private String resolveFromAccept(HttpServletRequest request) {
      String accept = request.getHeader("Accept");
      if (accept == null || accept.isBlank()) {
         return null;
      }

      Matcher m1 = MEDIA_TYPE_VERSION_PATTERN.matcher(accept);
      if (m1.find()) {
         return m1.group(1);
      }

      Matcher m2 = MEDIA_TYPE_PARAM_PATTERN.matcher(accept);
      if (m2.find()) {
         return m2.group(1);
      }

      return null;
   }
}
