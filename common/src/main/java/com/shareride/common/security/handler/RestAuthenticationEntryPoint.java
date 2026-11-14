package com.shareride.common.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.security.constant.CommonSecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Custom authentication entry point for REST APIs.
 *
 * <p>Intercepts authentication failures (401 Unauthorized) and serializes a standardized {@link
 * BaseResponse} JSON payload instead of redirecting to a login page or returning an HTML error.
 *
 * <p>This handler is registered in the stateless security filter chain and ensures all
 * unauthenticated requests receive a consistent REST-compliant error response.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        BaseResponse<Void> errorResponse =
                BaseResponse.error(
                        CommonSecurityConstants.ERROR_CODE_UNAUTHORIZED,
                        CommonSecurityConstants.ERROR_MESSAGE_UNAUTHORIZED,
                        null);

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
