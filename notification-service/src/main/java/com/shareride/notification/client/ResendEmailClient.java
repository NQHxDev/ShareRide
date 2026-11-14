package com.shareride.notification.client;

import com.shareride.notification.client.dto.ResendEmailRequest;
import com.shareride.notification.client.dto.ResendEmailResponse;
import com.shareride.notification.config.ResendProperties;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * REST client for Resend email delivery API with Resilience4j retry on transient errors.
 *
 * <p>Retry policy: Exponential backoff with initial delay 1s, multiplier 2.0, max attempts 3.
 *
 * <p>Retryable errors: HTTP 429 (Rate Limit), HTTP 5xx (Server Errors), Network Timeouts.
 *
 * <p>Non-retryable errors: HTTP 4xx (Client Errors like invalid email format).
 */
@Component
public class ResendEmailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailClient.class);

    private final RestClient restClient;
    private final ResendProperties resendProperties;

    public ResendEmailClient(RestClient restClient, ResendProperties resendProperties) {
        this.restClient = restClient;
        this.resendProperties = resendProperties;
    }

    public ResendEmailClient(ResendProperties resendProperties) {
        this.resendProperties = resendProperties;
        this.restClient =
                RestClient.builder()
                        .baseUrl(resendProperties.getApiUrl())
                        .defaultHeader(
                                HttpHeaders.AUTHORIZATION, "Bearer " + resendProperties.getApiKey())
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build();
    }

    /**
     * Sends email via Resend API with automatic retry on transient failures.
     *
     * <p>Resilience4j retry configuration (application.yml): - Initial delay: 1000ms - Multiplier:
     * 2.0 - Max attempts: 3 - Retryable exceptions: HttpServerErrorException,
     * ResourceAccessException
     *
     * @param request Email request payload
     * @return Response containing provider message ID
     * @throws Exception if all retry attempts are exhausted or permanent error occurs
     */
    @Retry(name = "resendEmail", fallbackMethod = "sendEmailFallback")
    public ResendEmailResponse sendEmail(ResendEmailRequest request) {
        log.info("Sending email to {} with subject: {}", request.getTo(), request.getSubject());

        try {
            ResendEmailResponse response =
                    restClient
                            .post()
                            .uri("/emails")
                            .body(request)
                            .retrieve()
                            .body(ResendEmailResponse.class);

            log.info("Email sent successfully. Provider message ID: {}", response.getId());
            return response;

        } catch (HttpServerErrorException e) {
            log.warn(
                    "Resend server error (HTTP {}). Will retry if attempts remain: {}",
                    e.getStatusCode().value(),
                    e.getMessage());
            throw e;

        } catch (ResourceAccessException e) {
            log.warn(
                    "Network timeout or connection error. Will retry if attempts remain: {}",
                    e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("Failed to send email via Resend (non-retryable): {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email via Resend", e);
        }
    }

    /**
     * Fallback method invoked after all retry attempts are exhausted.
     *
     * @param request Original email request
     * @param throwable Exception that caused all retries to fail
     * @return null to signal permanent failure
     */
    private ResendEmailResponse sendEmailFallback(ResendEmailRequest request, Throwable throwable) {
        log.error(
                "All retry attempts exhausted for email to {}. Marking as PERMANENTLY_FAILED: {}",
                request.getTo(),
                throwable.getMessage(),
                throwable);
        throw new RuntimeException("Email delivery permanently failed after retries", throwable);
    }
}
