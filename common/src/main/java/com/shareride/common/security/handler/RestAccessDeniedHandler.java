package com.shareride.common.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.security.constant.CommonSecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Custom access denied handler for REST APIs.
 *
 * <p>Intercepts authorization failures (403 Forbidden) when an authenticated user lacks the
 * required role or authority, and serializes a standardized {@link BaseResponse} JSON payload.
 *
 * <p>This handler is registered in the stateless security filter chain and ensures all
 * authorization denials return a consistent REST-compliant error response.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        BaseResponse<Void> errorResponse =
                BaseResponse.error(
                        CommonSecurityConstants.ERROR_CODE_FORBIDDEN,
                        CommonSecurityConstants.ERROR_MESSAGE_FORBIDDEN,
                        null);

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
