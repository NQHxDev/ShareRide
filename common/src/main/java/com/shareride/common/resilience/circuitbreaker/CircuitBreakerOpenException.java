package com.shareride.common.resilience.circuitbreaker;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.exception.BaseException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Thrown when an invocation is rejected because the target service's Circuit Breaker is OPEN. */
public class CircuitBreakerOpenException extends BaseException {

    private final String serviceName;

    public CircuitBreakerOpenException(String serviceName) {
        super(
                ResponseCode.CIRCUIT_BREAKER_OPEN.getCode(),
                "Downstream service ["
                        + serviceName
                        + "] is currently unavailable: Circuit breaker is OPEN",
                HttpStatus.SERVICE_UNAVAILABLE,
                Map.of("service", serviceName, "circuitBreaker", "OPEN"));
        this.serviceName = serviceName;
    }

    public CircuitBreakerOpenException(String serviceName, String details) {
        super(
                ResponseCode.CIRCUIT_BREAKER_OPEN.getCode(),
                "Downstream service [" + serviceName + "] is unavailable: " + details,
                HttpStatus.SERVICE_UNAVAILABLE,
                Map.of("service", serviceName, "circuitBreaker", "OPEN", "details", details));
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
