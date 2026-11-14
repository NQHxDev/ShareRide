package com.shareride.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "shareride.resend")
public class ResendProperties {

    private String apiKey;

    private String fromEmail;

    private String apiUrl = "https://api.resend.com";

    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 5000;
}
