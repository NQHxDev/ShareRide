package com.shareride.common.versioning;

import com.shareride.common.response.ResponseMetadataContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

/**
 * Spring MVC {@link RequestCondition} that routes HTTP requests to controller methods based on
 * declared {@link ApiVersion} annotations.
 */
public class ApiVersionRequestCondition implements RequestCondition<ApiVersionRequestCondition> {

    private final Set<String> versions;

    public ApiVersionRequestCondition(Collection<String> versions) {
        if (versions == null || versions.isEmpty()) {
            this.versions = Collections.emptySet();
        } else {
            Set<String> set = new HashSet<>();
            for (String v : versions) {
                set.add(ShareRideVersioningProperties.normalizeVersion(v));
            }
            this.versions = Collections.unmodifiableSet(set);
        }
    }

    public Set<String> getVersions() {
        return versions;
    }

    @Override
    public ApiVersionRequestCondition combine(ApiVersionRequestCondition other) {
        if (other.versions != null && !other.versions.isEmpty()) {
            return other;
        }
        return this;
    }

    @Override
    public ApiVersionRequestCondition getMatchingCondition(HttpServletRequest request) {
        if (this.versions.isEmpty()) {
            return this;
        }

        String activeVersion = ResponseMetadataContext.getApiVersion();
        String norm = ShareRideVersioningProperties.normalizeVersion(activeVersion);

        for (String v : this.versions) {
            if (v.equalsIgnoreCase(norm)) {
                return this;
            }
        }

        return null;
    }

    @Override
    public int compareTo(ApiVersionRequestCondition other, HttpServletRequest request) {
        return Integer.compare(other.versions.size(), this.versions.size());
    }
}
