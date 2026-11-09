package com.shareride.common.versioning;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.exception.BaseException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Thrown when an unsupported API version is requested. */
@Getter
public class UnsupportedApiVersionException extends BaseException {

    private final String requestedVersion;
    private final Set<String> supportedVersions;

    public UnsupportedApiVersionException(
            String requestedVersion, Collection<String> supportedVersions) {
        this(
                "API version '"
                        + requestedVersion
                        + "' is not supported. Supported versions: "
                        + supportedVersions,
                requestedVersion,
                supportedVersions);
    }

    public UnsupportedApiVersionException(
            String message, String requestedVersion, Collection<String> supportedVersions) {
        super(
                ResponseCode.UNSUPPORTED_API_VERSION.getCode(),
                message,
                HttpStatus.BAD_REQUEST,
                Map.of(
                        "version", requestedVersion != null ? requestedVersion : "unknown",
                        "supported",
                                supportedVersions != null
                                        ? String.join(", ", supportedVersions)
                                        : ""));
        this.requestedVersion = requestedVersion;
        this.supportedVersions =
                (supportedVersions != null)
                        ? Set.copyOf(supportedVersions)
                        : Collections.emptySet();
    }
}
