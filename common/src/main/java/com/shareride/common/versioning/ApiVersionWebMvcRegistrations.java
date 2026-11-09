package com.shareride.common.versioning;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Custom {@link WebMvcRegistrations} attaching {@link ApiVersionRequestCondition} to Spring MVC's
 * request mapping mechanism.
 */
public class ApiVersionWebMvcRegistrations implements WebMvcRegistrations {

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new ApiVersionRequestMappingHandlerMapping();
    }

    public static class ApiVersionRequestMappingHandlerMapping
            extends RequestMappingHandlerMapping {

        @Override
        protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
            ApiVersion apiVersion =
                    AnnotatedElementUtils.findMergedAnnotation(handlerType, ApiVersion.class);
            return (apiVersion != null)
                    ? new ApiVersionRequestCondition(Arrays.asList(apiVersion.value()))
                    : null;
        }

        @Override
        protected RequestCondition<?> getCustomMethodCondition(Method method) {
            ApiVersion apiVersion =
                    AnnotatedElementUtils.findMergedAnnotation(method, ApiVersion.class);
            return (apiVersion != null)
                    ? new ApiVersionRequestCondition(Arrays.asList(apiVersion.value()))
                    : null;
        }
    }
}
