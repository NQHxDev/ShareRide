package org.springdoc.webflux.ui;

import java.net.URI;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.ui.AbstractSwaggerWelcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.util.ForwardedHeaderUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Custom shadow of {@link org.springdoc.webflux.ui.SwaggerWelcomeCommon} to fix binary
 * incompatibility with Spring Framework 7 / Spring Boot 4.
 *
 * <p>Spring Framework 7 removed {@code UriComponentsBuilder.fromHttpRequest(HttpRequest)}. This
 * class replaces that invocation with {@link ForwardedHeaderUtils#adaptFromForwardedHeaders(URI,
 * org.springframework.http.HttpHeaders)}, preventing {@link NoSuchMethodError} when routing to
 * Swagger UI in Gateway.
 */
public abstract class SwaggerWelcomeCommon extends AbstractSwaggerWelcome {

    protected SwaggerWelcomeCommon(
            SwaggerUiConfigProperties swaggerUiConfig,
            SpringDocConfigProperties springDocConfigProperties) {
        super(swaggerUiConfig, springDocConfigProperties);
    }

    protected Mono<Void> redirectToUi(
            ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        SwaggerUiConfigParameters swaggerUiConfigParameters =
                new SwaggerUiConfigParameters(this.swaggerUiConfig);
        this.buildFromCurrentContextPath(swaggerUiConfigParameters, serverHttpRequest);
        String prefix = this.springDocConfigProperties.getWebjars().getPrefix();
        String swaggerPath = "/webjars".equals(prefix) ? "" : prefix;
        String swaggerUiUrl =
                swaggerUiConfigParameters.getContextPath()
                        + swaggerUiConfigParameters.getUiRootPath()
                        + swaggerPath
                        + this.getSwaggerUiUrl();
        UriComponentsBuilder uriComponentsBuilder =
                this.getUriComponentsBuilder(swaggerUiConfigParameters, swaggerUiUrl);
        serverHttpRequest.getQueryParams().forEach(uriComponentsBuilder::queryParam);
        serverHttpResponse.setStatusCode(HttpStatus.FOUND);
        serverHttpResponse
                .getHeaders()
                .setLocation(URI.create(uriComponentsBuilder.build().encode().toString()));
        return serverHttpResponse.setComplete();
    }

    @Override
    protected void calculateOauth2RedirectUrl(
            SwaggerUiConfigParameters swaggerUiConfigParameters,
            UriComponentsBuilder uriComponentsBuilder) {
        if (StringUtils.isBlank(this.swaggerUiConfig.getOauth2RedirectUrl())
                || !swaggerUiConfigParameters.isValidUrl(
                        this.swaggerUiConfig.getOauth2RedirectUrl())) {
            String webjarsPrefix = this.springDocConfigProperties.getWebjars().getPrefix();
            String additionalPath = "/webjars".equals(webjarsPrefix) ? "" : webjarsPrefix;
            swaggerUiConfigParameters.setOauth2RedirectUrl(
                    uriComponentsBuilder
                            .path(swaggerUiConfigParameters.getUiRootPath())
                            .path(additionalPath)
                            .path(this.getOauth2RedirectUrl())
                            .build()
                            .toString());
        }
    }

    protected Map<String, Object> getSwaggerUiConfig(ServerHttpRequest request) {
        SwaggerUiConfigParameters swaggerUiConfigParameters =
                new SwaggerUiConfigParameters(this.swaggerUiConfig);
        this.buildFromCurrentContextPath(swaggerUiConfigParameters, request);
        return swaggerUiConfigParameters.getConfigParameters();
    }

    void buildFromCurrentContextPath(
            SwaggerUiConfigParameters swaggerUiConfigParameters, ServerHttpRequest request) {
        super.init(swaggerUiConfigParameters);
        swaggerUiConfigParameters.setContextPath(request.getPath().contextPath().value());
        String url =
                ForwardedHeaderUtils.adaptFromForwardedHeaders(
                                request.getURI(), request.getHeaders())
                        .build()
                        .toUriString();
        String target =
                UriComponentsBuilder.fromPath(request.getPath().contextPath().value())
                        .toUriString();
        int endIndex = url.indexOf(target) + target.length();
        if (endIndex > 0) {
            url = url.substring(0, endIndex);
        } else {
            url = request.getPath().toString();
        }
        this.buildConfigUrl(swaggerUiConfigParameters, UriComponentsBuilder.fromUriString(url));
    }
}
