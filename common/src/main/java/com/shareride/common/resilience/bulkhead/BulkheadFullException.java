package com.shareride.common.resilience.bulkhead;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.exception.BaseException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Thrown when the Bulkhead concurrency capacity for a service is completely saturated. */
public class BulkheadFullException extends BaseException {

    private final String serviceName;

    public BulkheadFullException(String serviceName, int maxConcurrentCalls) {
        super(
                ResponseCode.TOO_MANY_REQUESTS.getCode(),
                "Concurrency bulkhead saturated for service ["
                        + serviceName
                        + "] (limit: "
                        + maxConcurrentCalls
                        + ")",
                HttpStatus.TOO_MANY_REQUESTS,
                Map.of("service", serviceName, "maxConcurrentCalls", maxConcurrentCalls));
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
