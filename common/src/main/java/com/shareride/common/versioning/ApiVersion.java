package com.shareride.common.versioning;

import java.lang.annotation.*;

/**
 * Declares supported API version(s) for a REST Controller class or individual handler method.
 * <p>
 * Example usage:
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/rides")
 * @ApiVersion("v1")
 * public class RideControllerV1 { ... }
 *
 * @GetMapping
 * @ApiVersion({"v1", "v2"})
 * public BaseResponse<List<RideDto>> listRides() { ... }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

   /**
    * List of supported API versions (e.g. "v1", "v2", "v2.1").
    */
   String[] value() default {"v1"};

   /**
    * Whether this endpoint or version is deprecated.
    */
   boolean deprecated() default false;

   /**
    * Scheduled sunset date if deprecated (e.g. "2026-12-31").
    */
   String sunset() default "";

   /**
    * Suggested successor endpoint or successor version (e.g. "/api/v2/rides").
    */
   String successor() default "";
}
