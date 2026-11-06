package com.shareride.common;

import com.shareride.common.response.BaseResponse;
import com.shareride.common.response.ResponseMetadataContext;
import com.shareride.common.versioning.ApiVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ApiVersioningIntegrationTest.TestApplication.class)
@TestPropertySource(properties = {
      "shareride.redis.enabled=false",
      "management.health.rabbit.enabled=false",
      "shareride.versioning.enabled=true",
      "shareride.versioning.default-version=v1",
      "shareride.versioning.supported-versions=v1,v2",
      "shareride.versioning.deprecated-versions=v1",
      "shareride.versioning.sunset-dates.v1=2026-12-31",
      "shareride.versioning.enforcement-mode=STRICT",
      "spring.main.banner-mode=off"
})
class ApiVersioningIntegrationTest {

   @SpringBootApplication(scanBasePackages = "com.shareride.common", exclude = {
         org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration.class,
         org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration.class
   })
   @Import({TestVersioningController.class, com.shareride.common.versioning.ApiVersionWebMvcRegistrations.class})
   static class TestApplication {
   }

   @RestController
   static class TestVersioningController {

      @GetMapping("/api/v1/greeting")
      public BaseResponse<String> greetingV1() {
         return BaseResponse.<String>builder()
               .data("Hello from v1")
               .autoMetadata()
               .build();
      }

      @GetMapping("/api/v2/greeting")
      public BaseResponse<String> greetingV2() {
         return BaseResponse.<String>builder()
               .data("Hello from v2")
               .autoMetadata()
               .build();
      }

      @GetMapping("/api/resource")
      @ApiVersion("v1")
      public BaseResponse<String> resourceV1() {
         return BaseResponse.<String>builder()
               .data("resource-content-v1")
               .autoMetadata()
               .build();
      }

      @GetMapping("/api/resource")
      @ApiVersion("v2")
      public BaseResponse<String> resourceV2() {
         return BaseResponse.<String>builder()
               .data("resource-content-v2")
               .autoMetadata()
               .build();
      }
   }

   @Autowired
   private WebApplicationContext webApplicationContext;

   private MockMvc mockMvc;

   @BeforeEach
   void setUp() {
      mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilter(webApplicationContext.getBean(com.shareride.common.versioning.ApiVersionEnforcementFilter.class)).build();
      ResponseMetadataContext.clear();
   }

   @AfterEach
   void cleanup() {
      ResponseMetadataContext.clear();
   }

   @Test
   @DisplayName("Calling /api/v2/greeting should return 200 with X-API-Version=v2 and metadata")
   void testV2Greeting() throws Exception {
      mockMvc.perform(get("/api/v2/greeting"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-API-Version", "v2"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value("Hello from v2"))
            .andExpect(jsonPath("$.metadata.apiVersion").value("v2"));
   }

   @Test
   @DisplayName("Calling deprecated v1 should return 200 with Deprecation & Sunset headers, and metadata warning")
   void testDeprecatedV1Greeting() throws Exception {
      mockMvc.perform(get("/api/v1/greeting"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-API-Version", "v1"))
            .andExpect(header().string("Deprecation", "true"))
            .andExpect(header().string("Sunset", "2026-12-31"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value("Hello from v1"))
            .andExpect(jsonPath("$.metadata.apiVersion").value("v1"))
            .andExpect(jsonPath("$.warnings", hasItem(containsString("deprecated"))));
   }

   @Test
   @DisplayName("Requesting unsupported version via Header in STRICT mode should return 400 Bad Request")
   void testUnsupportedVersionHeaderRejection() throws Exception {
      mockMvc.perform(get("/api/resource").header("X-API-Version", "v99"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_API_VERSION"))
            .andExpect(jsonPath("$.message", containsString("v99")));
   }

   @Test
   @DisplayName("Same URI /api/resource dispatches to v1 or v2 controller based on X-API-Version header")
   void testVersionConditionRouting() throws Exception {
      // Call with header X-API-Version: v1
      mockMvc.perform(get("/api/resource").header("X-API-Version", "v1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("resource-content-v1"));

      // Call with header X-API-Version: v2
      mockMvc.perform(get("/api/resource").header("X-API-Version", "v2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("resource-content-v2"));
   }
}
