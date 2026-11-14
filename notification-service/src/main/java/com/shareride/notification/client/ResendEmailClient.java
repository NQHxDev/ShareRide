package com.shareride.notification.client;

import com.shareride.notification.client.dto.ResendEmailRequest;
import com.shareride.notification.client.dto.ResendEmailResponse;
import com.shareride.notification.config.ResendProperties;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
 * <p>Non-retryable errors: HTTP 4xx except 429 (Client Errors like invalid email format).
 */
@Component
public class ResendEmailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailClient.class);

    private final RestClient restClient;

    @Autowired
    public ResendEmailClient(
            ResendProperties resendProperties, @Autowired(required = false) RestClient restClient) {
        if (restClient != null) {
            this.restClient = restClient;
        } else {
            this.restClient =
                    RestClient.builder()
                            .baseUrl(resendProperties.getApiUrl())
                            .defaultHeader(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + resendProperties.getApiKey())
                            .defaultHeader(
                                    HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .build();
        }
    }

    public ResendEmailClient(ResendProperties resendProperties) {
        this(resendProperties, null);
    }

    public ResendEmailClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Sends email via Resend API with automatic retry on transient failures.
     *
     * <p>Resilience4j retry configuration (application.yml): - Initial delay: 1000ms - Multiplier:
     * 2.0 - Max attempts: 3 - Retryable exceptions: HttpServerErrorException,
     * ResourceAccessException, HttpClientErrorException with status 429
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

        } catch (HttpClientErrorException e) {
            // Retry only on HTTP 429 Too Many Requests (rate limiting)
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn(
                        "Resend rate limit exceeded (HTTP 429). Will retry if attempts remain: {}",
                        e.getMessage());
                throw e;
            } else {
                // Other 4xx errors are permanent (e.g., invalid email, auth failure)
                log.error(
                        "Resend client error (HTTP {}). Non-retryable: {}",
                        e.getStatusCode().value(),
                        e.getMessage());
                throw new RuntimeException("Non-retryable client error", e);
            }

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
            log.error("Unexpected error sending email via Resend: {}", e.getMessage(), e);
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
    ResendEmailResponse sendEmailFallback(ResendEmailRequest request, Throwable throwable) {
        log.error(
                "All retry attempts exhausted for email to {}. Marking as PERMANENTLY_FAILED: {}",
                request.getTo(),
                throwable.getMessage(),
                throwable);
        throw new RuntimeException("Email delivery permanently failed after retries", throwable);
    }
}
