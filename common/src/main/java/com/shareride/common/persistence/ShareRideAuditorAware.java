package com.shareride.common.persistence;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;

/**
 * Spring Data JPA AuditorAware implementation bridging UserContextHolder to @CreatedBy
 * and @LastModifiedBy fields.
 */
public class ShareRideAuditorAware implements AuditorAware<String> {

    public static final String SYSTEM_USER = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        UserContext context = UserContextHolder.getContext();
        if (context != null && context.getUserId() != null && !context.getUserId().isBlank()) {
            return Optional.of(context.getUserId());
        }
        return Optional.of(SYSTEM_USER);
    }
}
