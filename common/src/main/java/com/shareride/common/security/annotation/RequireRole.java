package com.shareride.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative role-based authorization guard.
 *
 * <p>Restricts access to methods or classes to callers possessing the specified roles. Evaluated by
 * {@link com.shareride.common.security.aspect.SecurityAuthorizationAspect}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RequireRole {

    /** Array of role codes required (e.g. "ROLE_ADMIN", "ADMIN", "ROLE_DRIVER"). */
    String[] value();

    /** Logical evaluation condition (defaults to OR - any of the roles). */
    LogicalOperator logical() default LogicalOperator.OR;
}
