package com.shareride.notification.consumer;

import com.shareride.common.util.UuidUtils;
import com.shareride.notification.client.ResendEmailClient;
import com.shareride.notification.client.dto.ResendEmailRequest;
import com.shareride.notification.client.dto.ResendEmailResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Test configuration that provides a mock ResendEmailClient to avoid actual HTTP calls during
 * integration tests.
 */
@TestConfiguration
public class TestEmailClientConfiguration {

    @Bean
    @Primary
    public ResendEmailClient resendEmailClient() {
        // Create a mock RestClient (won't be used in the override)
        RestClient mockRestClient = RestClient.builder().build();

        return new ResendEmailClient(mockRestClient) {
            @Override
            public ResendEmailResponse sendEmail(ResendEmailRequest request) {
                // Mock successful email send
                ResendEmailResponse response = new ResendEmailResponse();
                response.setId(UuidUtils.v7AsString());
                return response;
            }
        };
    }
}
