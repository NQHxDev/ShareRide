package com.shareride.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative permission-based authorization guard.
 *
 * <p>Restricts access to methods or classes to callers possessing the specified fine-grained
 * permission codes. Evaluated by {@link
 * com.shareride.common.security.aspect.SecurityAuthorizationAspect}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RequirePermission {

    /** Array of permission codes required (e.g. "account:read", "ride:accept"). */
    String[] value();

    /** Logical evaluation condition (defaults to OR - any of the permissions). */
    LogicalOperator logical() default LogicalOperator.OR;
}
