package com.shareride.notification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.notification.client.dto.ResendEmailRequest;
import com.shareride.notification.client.dto.ResendEmailResponse;
import com.shareride.notification.config.ResendProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailClientTest {

    private ResendEmailClient resendEmailClient;
    private MockRestServiceServer mockServer;
    private ResendProperties resendProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resendProperties = new ResendProperties();
        resendProperties.setApiKey("test_api_key_12345");
        resendProperties.setFromEmail("test@shareride.vn");
        resendProperties.setApiUrl("https://api.resend.com");

        objectMapper = new ObjectMapper();

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient =
                builder.baseUrl(resendProperties.getApiUrl())
                        .defaultHeader(
                                HttpHeaders.AUTHORIZATION, "Bearer " + resendProperties.getApiKey())
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build();

        resendEmailClient = new ResendEmailClient(restClient, resendProperties);
    }

    @Test
    void sendEmail_shouldSendRequestWithCorrectHeadersAndPayload() throws Exception {
        ResendEmailRequest request =
                ResendEmailRequest.builder()
                        .from("test@shareride.vn")
                        .to(List.of("user@example.com"))
                        .subject("Test Subject")
                        .html("<p>Test HTML</p>")
                        .text("Test Plain Text")
                        .build();

        ResendEmailResponse expectedResponse = new ResendEmailResponse();
        expectedResponse.setId("msg_abc123xyz");

        mockServer
                .expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test_api_key_12345"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.from").value("test@shareride.vn"))
                .andExpect(jsonPath("$.to[0]").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Test Subject"))
                .andExpect(jsonPath("$.html").value("<p>Test HTML</p>"))
                .andExpect(jsonPath("$.text").value("Test Plain Text"))
                .andRespond(
                        withSuccess(
                                objectMapper.writeValueAsString(expectedResponse),
                                MediaType.APPLICATION_JSON));

        ResendEmailResponse response = resendEmailClient.sendEmail(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("msg_abc123xyz");
        mockServer.verify();
    }

    @Test
    void sendEmail_shouldHandleServerError() {
        ResendEmailRequest request =
                ResendEmailRequest.builder()
                        .from("test@shareride.vn")
                        .to(List.of("user@example.com"))
                        .subject("Test Subject")
                        .html("<p>Test HTML</p>")
                        .build();

        mockServer
                .expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> resendEmailClient.sendEmail(request))
                .isInstanceOf(RuntimeException.class);

        mockServer.verify();
    }

    @Test
    void sendEmail_shouldHandleRateLimitError() {
        ResendEmailRequest request =
                ResendEmailRequest.builder()
                        .from("test@shareride.vn")
                        .to(List.of("user@example.com"))
                        .subject("Test Subject")
                        .html("<p>Test HTML</p>")
                        .build();

        mockServer
                .expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> resendEmailClient.sendEmail(request))
                .isInstanceOf(RuntimeException.class);

        mockServer.verify();
    }
}
