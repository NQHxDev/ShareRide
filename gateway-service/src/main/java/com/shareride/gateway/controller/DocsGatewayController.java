package com.shareride.gateway.controller;

import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Controller providing convenience redirects for API documentation.
 *
 * <p>Redirects root path {@code /}, {@code /docs} and {@code /docs/} to the Swagger UI endpoint
 * ({@code /swagger-ui/index.html}).
 */
@Hidden
@RestController
public class DocsGatewayController {

    private static final URI SWAGGER_UI_URI = URI.create("/swagger-ui/index.html");

    @GetMapping(value = {"/", "/docs", "/docs/"})
    public Mono<ResponseEntity<Void>> redirectToSwaggerUi() {
        return Mono.just(ResponseEntity.status(HttpStatus.FOUND).location(SWAGGER_UI_URI).build());
    }
}
