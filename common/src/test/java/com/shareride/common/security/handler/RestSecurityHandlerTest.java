package com.shareride.common.security.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.security.constant.CommonSecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/**
 * Unit tests for REST security handlers.
 *
 * <p>Verifies that {@link RestAuthenticationEntryPoint} and {@link RestAccessDeniedHandler}
 * serialize 401 and 403 errors as {@link BaseResponse} JSON without booting full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class RestSecurityHandlerTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private ObjectMapper objectMapper;
    private RestAuthenticationEntryPoint authenticationEntryPoint;
    private RestAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);
        accessDeniedHandler = new RestAccessDeniedHandler(objectMapper);
    }

    @Test
    void commence_shouldReturn401WithBaseResponseJson() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        AuthenticationException authException = mock(AuthenticationException.class);

        authenticationEntryPoint.commence(request, response, authException);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(response).setCharacterEncoding("UTF-8");

        writer.flush();
        String jsonResponse = stringWriter.toString();
        BaseResponse<?> responseObject = objectMapper.readValue(jsonResponse, BaseResponse.class);

        assertThat(responseObject.getCode())
                .isEqualTo(CommonSecurityConstants.ERROR_CODE_UNAUTHORIZED);
        assertThat(responseObject.getMessage())
                .isEqualTo(CommonSecurityConstants.ERROR_MESSAGE_UNAUTHORIZED);
        assertThat(responseObject.getData()).isNull();
        assertThat(responseObject.getTimestamp()).isNotNull();
    }

    @Test
    void handle_shouldReturn403WithBaseResponseJson() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        AccessDeniedException accessDeniedException = new AccessDeniedException("Forbidden");

        accessDeniedHandler.handle(request, response, accessDeniedException);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(response).setCharacterEncoding("UTF-8");

        writer.flush();
        String jsonResponse = stringWriter.toString();
        BaseResponse<?> responseObject = objectMapper.readValue(jsonResponse, BaseResponse.class);

        assertThat(responseObject.getCode())
                .isEqualTo(CommonSecurityConstants.ERROR_CODE_FORBIDDEN);
        assertThat(responseObject.getMessage())
                .isEqualTo(CommonSecurityConstants.ERROR_MESSAGE_FORBIDDEN);
        assertThat(responseObject.getData()).isNull();
        assertThat(responseObject.getTimestamp()).isNotNull();
    }
}
