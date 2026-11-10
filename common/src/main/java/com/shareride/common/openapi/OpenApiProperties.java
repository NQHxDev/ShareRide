package com.shareride.common.openapi;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for OpenAPI 3.0 / Swagger living documentation across ShareRide
 * microservices.
 */
@Data
@ConfigurationProperties(prefix = "shareride.openapi")
public class OpenApiProperties {

    /** Enable or disable OpenAPI documentation generation. Defaults to {@code true}. */
    private boolean enabled = true;

    /** Title of the microservice API documentation. */
    private String title = "ShareRide API";

    /** Detailed description of the microservice APIs. */
    private String description =
            "ShareRide Microservice RESTful APIs with Standardized BaseResponse, EdDSA JWT"
                    + " Security, and RFC Observability";

    /** Version string for the API documentation. */
    private String version = "v1";

    /** Name of the contact or engineering team. */
    private String contactName = "ShareRide Platform Engineering Team";

    /** Email of the contact or engineering team. */
    private String contactEmail = "platform-engineering@shareride.com";

    /** License name. */
    private String licenseName = "Apache 2.0";

    /** License URL. */
    private String licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0";

    /** Terms of Service URL. */
    private String termsOfService = "https://shareride.com/terms";
}
