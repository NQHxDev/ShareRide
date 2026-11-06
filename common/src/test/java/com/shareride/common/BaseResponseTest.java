package com.shareride.common;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.i18n.TemplateMessageSource;
import com.shareride.common.response.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseResponseTest {

   private ObjectMapper objectMapper;
   private TemplateMessageSource templateMessageSource;

   @BeforeEach
   void setUp() {
      objectMapper = JsonMapper.builder().build();
      TraceIdContext.setTraceId("test-trace-id-123");
      TraceIdContext.setStartTime(System.currentTimeMillis() - 50);

      templateMessageSource = new TemplateMessageSource();
      templateMessageSource.init();

      ResponseMetadataContext.clear();
      LocaleContextHolder.resetLocaleContext();
   }

   @AfterEach
   void tearDown() {
      TraceIdContext.clear();
      ResponseMetadataContext.clear();
      LocaleContextHolder.resetLocaleContext();
   }

   // -------------------------------------------------------------
   // Requirement 1: Pagination support (PageInfo & paginate)
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should correctly calculate PageInfo metadata for first, middle, and last pages")
   void testPageInfoCalculation() {
      // First page of 3
      PageInfo firstPage = PageInfo.of(0, 10, 25);
      assertEquals(0, firstPage.getPage());
      assertEquals(10, firstPage.getSize());
      assertEquals(25, firstPage.getTotalElements());
      assertEquals(3, firstPage.getTotalPages());
      assertTrue(firstPage.isFirst());
      assertFalse(firstPage.isLast());
      assertTrue(firstPage.isHasNext());
      assertFalse(firstPage.isHasPrevious());

      // Middle page
      PageInfo midPage = PageInfo.of(1, 10, 25);
      assertFalse(midPage.isFirst());
      assertFalse(midPage.isLast());
      assertTrue(midPage.isHasNext());
      assertTrue(midPage.isHasPrevious());

      // Last page
      PageInfo lastPage = PageInfo.of(2, 10, 25);
      assertFalse(lastPage.isFirst());
      assertTrue(lastPage.isLast());
      assertFalse(lastPage.isHasNext());
      assertTrue(lastPage.isHasPrevious());
   }

   @Test
   @DisplayName("Should create paginated BaseResponse via factory methods and builder")
   void testPaginatedResponse() {
      List<String> items = List.of("item1", "item2");
      BaseResponse<List<String>> res1 = BaseResponse.paginate(items, 0, 10, 2, "Items retrieved");

      assertTrue(res1.isSuccess());
      assertEquals(ResponseLevel.SUCCESS, res1.getLevel());
      assertEquals("Items retrieved", res1.getMessage());
      assertEquals(items, res1.getData());
      assertNotNull(res1.getPagination());
      assertEquals(1, res1.getPagination().getTotalPages());
      assertTrue(res1.getPagination().isFirst());
      assertTrue(res1.getPagination().isLast());

      // Via Builder
      BaseResponse<List<String>> res2 = BaseResponse.<List<String>>builder()
            .pagination(1, 10, 50)
            .data(items)
            .build();
      assertNotNull(res2.getPagination());
      assertEquals(1, res2.getPagination().getPage());
      assertEquals(5, res2.getPagination().getTotalPages());
   }

   // -------------------------------------------------------------
   // Requirement 2: Extended metadata (duration, version, env, attributes)
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should populate extended metadata manually and via autoMetadata()")
   void testExtendedMetadata() {
      ResponseMetadataContext.set("v2.1", "production");

      BaseResponse<String> res = BaseResponse.<String>builder()
            .data("payload")
            .autoMetadata()
            .attribute("region", "ap-southeast-1")
            .attribute("node", "worker-42")
            .build();

      assertNotNull(res.getMetadata());
      assertEquals("v2.1", res.getMetadata().getApiVersion());
      assertEquals("production", res.getMetadata().getEnvironment());
      assertTrue(res.getMetadata().getDurationMs() >= 0);
      assertEquals("ap-southeast-1", res.getMetadata().getAttributes().get("region"));
      assertEquals("worker-42", res.getMetadata().getAttributes().get("node"));
   }

   // -------------------------------------------------------------
   // Requirement 3: Nested validation and error hierarchy
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should build hierarchical nested validation errors from dotted field names")
   void testNestedValidationErrors() {
      List<FieldErrorDetail> flatErrors = List.of(
            FieldErrorDetail.of("user.address.street", "Street is required"),
            FieldErrorDetail.of("user.address.city", "City is required"),
            FieldErrorDetail.of("user.email", "invalid@format", "Email is invalid"),
            FieldErrorDetail.of("role", "ROLE_ADMIN", "Forbidden role", "INVALID_ROLE"));

      List<FieldErrorDetail> hierarchical = FieldErrorDetail.buildHierarchicalErrors(flatErrors);

      // Root should contain: "role", and parent "user"
      assertEquals(2, hierarchical.size());

      FieldErrorDetail userError = hierarchical.stream()
            .filter(e -> "user".equals(e.getField()))
            .findFirst()
            .orElseThrow();

      assertTrue(userError.hasNestedErrors());
      assertEquals(2, userError.getNestedErrors().size()); // "address" and "email"

      FieldErrorDetail addressError = userError.getNestedErrors().stream()
            .filter(e -> "address".equals(e.getField()))
            .findFirst()
            .orElseThrow();

      assertTrue(addressError.hasNestedErrors());
      assertEquals(2, addressError.getNestedErrors().size()); // "street" and "city"
   }

   // -------------------------------------------------------------
   // Requirement 4: Warning & Info levels (ResponseLevel enum)
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should support ResponseLevel INFO, WARNING, and status check helpers")
   void testResponseLevels() {
      // Warning
      BaseResponse<String> warnRes = BaseResponse.<String>warning("data", "Feature will be deprecated",
            List.of("Warn 1", "Warn 2"));
      assertTrue(warnRes.isSuccess());
      assertTrue(warnRes.isWarning());
      assertFalse(warnRes.isInfo());
      assertFalse(warnRes.isError());
      assertEquals(ResponseLevel.WARNING, warnRes.getLevel());
      assertEquals(2, warnRes.getWarnings().size());

      // Info
      BaseResponse<String> infoRes = BaseResponse.info("data", "Informational note");
      assertTrue(infoRes.isSuccess());
      assertTrue(infoRes.isInfo());
      assertFalse(infoRes.isWarning());
      assertFalse(infoRes.isError());
      assertEquals(ResponseLevel.INFO, infoRes.getLevel());

      // Error
      BaseResponse<Void> errRes = BaseResponse.error("ERR_CODE", "Something broke");
      assertFalse(errRes.isSuccess());
      assertTrue(errRes.isError());
      assertFalse(errRes.isInfo());
      assertFalse(errRes.isWarning());
      assertEquals(ResponseLevel.ERROR, errRes.getLevel());
   }

   // -------------------------------------------------------------
   // Requirement 5: New ResponseCodes (Rate limit, timeout, circuit breaker)
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should properly recognize new response codes")
   void testNewResponseCodes() {
      assertEquals("RATE_LIMIT_EXCEEDED", ResponseCode.RATE_LIMIT_EXCEEDED.getCode());
      assertEquals(429, ResponseCode.RATE_LIMIT_EXCEEDED.getHttpStatus().value());

      assertEquals("REQUEST_TIMEOUT", ResponseCode.REQUEST_TIMEOUT.getCode());
      assertEquals(408, ResponseCode.REQUEST_TIMEOUT.getHttpStatus().value());

      assertEquals("CIRCUIT_BREAKER_OPEN", ResponseCode.CIRCUIT_BREAKER_OPEN.getCode());
      assertEquals(503, ResponseCode.CIRCUIT_BREAKER_OPEN.getHttpStatus().value());

      assertEquals("PAYMENT_REQUIRED", ResponseCode.PAYMENT_REQUIRED.getCode());
      assertEquals(402, ResponseCode.PAYMENT_REQUIRED.getHttpStatus().value());
   }

   // -------------------------------------------------------------
   // Requirement 6: Localization support for messages
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should resolve localized messages in Vietnamese and English via factory and builder")
   void testMessageLocalization() {
      // Vietnamese locale
      LocaleContextHolder.setLocale(Locale.of("vi"));

      BaseResponse<String> viSuccess = BaseResponse.successLocalized("SUCCESS", "data");
      assertEquals("Thành công", viSuccess.getMessage());

      BaseResponse<Void> viRateLimit = BaseResponse.errorLocalized(ResponseCode.RATE_LIMIT_EXCEEDED);
      assertEquals("Đã vượt quá giới hạn tần suất yêu cầu, vui lòng thử lại sau", viRateLimit.getMessage());

      BaseResponse<Void> viTimeout = BaseResponse.errorLocalized(ResponseCode.REQUEST_TIMEOUT);
      assertEquals("Hết thời gian chờ yêu cầu", viTimeout.getMessage());

      // English locale
      LocaleContextHolder.setLocale(Locale.ENGLISH);

      BaseResponse<Void> enRateLimit = BaseResponse.errorLocalized(ResponseCode.RATE_LIMIT_EXCEEDED);
      assertEquals("Rate limit exceeded, please try again later", enRateLimit.getMessage());

      // Japanese locale via builder codeAndMessage
      BaseResponse<Void> jaRes = BaseResponse.<Void>builder()
            .fromResponseCode(ResponseCode.CIRCUIT_BREAKER_OPEN, Map.of(), Locale.JAPANESE)
            .build();
      assertEquals("サーキットブレーカーが作動中です。しばらくしてから再試行してください", jaRes.getMessage());
   }

   // -------------------------------------------------------------
   // Requirement 7: Advanced fluent Builder
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should build complex response fluently with all features")
   void testAdvancedBuilderChaining() {
      BaseResponse<String> res = BaseResponse.<String>builder()
            .level(ResponseLevel.WARNING)
            .code(ResponseCode.SUCCESS)
            .message("Process completed with non-fatal warnings")
            .data("result-token")
            .pagination(PageInfo.of(0, 10, 1))
            .duration(75L)
            .apiVersion("v1.0")
            .environment("development")
            .addWarning("Token expires in 5 minutes")
            .path("/api/v1/auth/token")
            .build();

      assertTrue(res.isSuccess());
      assertTrue(res.isWarning());
      assertEquals("SUCCESS", res.getCode());
      assertEquals("result-token", res.getData());
      assertEquals(1, res.getWarnings().size());
      assertEquals(75L, res.getMetadata().getDurationMs());
      assertEquals("v1.0", res.getMetadata().getApiVersion());
      assertEquals("development", res.getMetadata().getEnvironment());
      assertEquals("test-trace-id-123", res.getTraceId());
      assertNotNull(res.getTimestamp());
   }

   // -------------------------------------------------------------
   // JSON Serialization verification
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should properly serialize all new fields into JSON omitting nulls")
   void testJsonSerializationWithAllFields() throws Exception {
      BaseResponse<String> res = BaseResponse.<String>builder()
            .level(ResponseLevel.WARNING)
            .code(ResponseCode.SUCCESS.getCode())
            .message("Warning message")
            .data("payload")
            .pagination(PageInfo.of(0, 20, 100))
            .duration(23L)
            .apiVersion("v1.0")
            .environment("prod")
            .addWarning("Slow query")
            .path("/api/v1/rides")
            .build();

      String json = objectMapper.writeValueAsString(res);

      assertTrue(json.contains("\"success\":true"));
      assertTrue(json.contains("\"level\":\"WARNING\""));
      assertTrue(json.contains("\"code\":\"SUCCESS\""));
      assertTrue(json.contains("\"pagination\":{"));
      assertTrue(json.contains("\"totalElements\":100"));
      assertTrue(json.contains("\"totalPages\":5"));
      assertTrue(json.contains("\"metadata\":{"));
      assertTrue(json.contains("\"durationMs\":23"));
      assertTrue(json.contains("\"apiVersion\":\"v1.0\""));
      assertTrue(json.contains("\"warnings\":[\"Slow query\"]"));
      assertFalse(json.contains("\"errors\"")); // errors is null, so omitted
   }

   // -------------------------------------------------------------
   // Null safety checks and factory method validations
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should validate factory method parameters and throw IllegalArgumentException on invalid input")
   void testValidationForFactoryMethods() {
      // Null ResponseCode validations
      IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.error((ResponseCode) null, "Message"));
      assertEquals("ResponseCode cannot be null", e1.getMessage());

      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.errorLocalized((ResponseCode) null));
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.errorLocalized((ResponseCode) null, Map.of()));
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.errorLocalized((ResponseCode) null, List.of()));
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.warning((ResponseCode) null, "warn", "data"));
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.warningLocalized((ResponseCode) null, "data"));
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.infoLocalized((ResponseCode) null, "data"));

      // Pagination parameter validations
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.paginate("data", -1, 10, 100));
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.paginate("data", 0, 0, 100));
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.paginate("data", 0, -5, 100));
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.paginate("data", 0, 10, -1));
      assertThrows(IllegalArgumentException.class,
            () -> BaseResponse.<String>builder().pagination(-1, 10, 100));
      assertThrows(IllegalArgumentException.class,
            () -> PageInfo.of(-1, 10, 100));
   }

   // -------------------------------------------------------------
   // Consistent method naming & deprecated aliases
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should support warning() with string code and deprecated warningWithCode()")
   void testConsistentWarningMethodNaming() {
      BaseResponse<String> res1 = BaseResponse.warning("CUSTOM_WARN", "Pay attention", "warn-payload");
      assertEquals("CUSTOM_WARN", res1.getCode());
      assertEquals(ResponseLevel.WARNING, res1.getLevel());
      assertEquals("Pay attention", res1.getMessage());
      assertEquals("warn-payload", res1.getData());

      @SuppressWarnings("deprecation")
      BaseResponse<String> res2 = BaseResponse.warningWithCode("DEPR_WARN", "Old warning style", "data");
      assertEquals("DEPR_WARN", res2.getCode());
      assertEquals(ResponseLevel.WARNING, res2.getLevel());
   }

   @Test
   @DisplayName("Should support builder fromResponseCode() and deprecated codeAndMessage()")
   void testFromResponseCodeAndDeprecatedCodeAndMessage() {
      BaseResponse<Void> res1 = BaseResponse.<Void>builder()
            .fromResponseCode(ResponseCode.PAYMENT_REQUIRED)
            .build();
      assertEquals("PAYMENT_REQUIRED", res1.getCode());
      assertNotNull(res1.getMessage());

      @SuppressWarnings("deprecation")
      BaseResponse<Void> res2 = BaseResponse.<Void>builder()
            .codeAndMessage(ResponseCode.UNAUTHORIZED)
            .build();
      assertEquals("UNAUTHORIZED", res2.getCode());
      assertNotNull(res2.getMessage());
   }

   // -------------------------------------------------------------
   // Utility methods: hasErrors, hasWarnings, getTotalErrorCount
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should correctly evaluate utility methods hasErrors, hasWarnings, getTotalErrorCount")
   void testUtilityMethods() {
      BaseResponse<String> emptyRes = BaseResponse.success("ok");
      assertFalse(emptyRes.hasErrors());
      assertFalse(emptyRes.hasWarnings());
      assertEquals(0, emptyRes.getTotalErrorCount());

      FieldErrorDetail leaf1 = FieldErrorDetail.of("street", "Street required");
      FieldErrorDetail leaf2 = FieldErrorDetail.of("city", "City required");
      FieldErrorDetail parent = FieldErrorDetail.builder()
            .field("address")
            .nestedErrors(List.of(leaf1, leaf2))
            .build();

      BaseResponse<Void> errRes = BaseResponse.<Void>builder()
            .addError(FieldErrorDetail.of("email", "Email required"))
            .addError(parent)
            .addWarning("Consider updating your profile")
            .build();

      assertTrue(errRes.hasErrors());
      assertTrue(errRes.hasWarnings());
      // 1 (email) + (1 + 2 nested) = 4 total error count
      assertEquals(4, errRes.getTotalErrorCount());
   }

   // -------------------------------------------------------------
   // PageInfo improvements (isEmpty, getOffset, empty)
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should verify PageInfo utility methods isEmpty, getOffset, empty")
   void testPageInfoUtilities() {
      PageInfo emptyPage = PageInfo.empty();
      assertTrue(emptyPage.isEmpty());
      assertEquals(0, emptyPage.getOffset());
      assertEquals(0, emptyPage.getPage());
      assertEquals(0, emptyPage.getSize());
      assertEquals(0, emptyPage.getTotalElements());

      PageInfo page = PageInfo.of(3, 25, 100);
      assertFalse(page.isEmpty());
      assertEquals(75, page.getOffset());
   }

   // -------------------------------------------------------------
   // ResponseMetadata improvements (withDuration, auto)
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should verify ResponseMetadata withDuration and auto factory methods")
   void testResponseMetadataImprovements() {
      ResponseMetadata mdDuration = ResponseMetadata.withDuration(150L);
      assertEquals(150L, mdDuration.getDurationMs());
      assertNull(mdDuration.getApiVersion());
      assertNull(mdDuration.getEnvironment());

      ResponseMetadataContext.set("v3.0", "staging");
      ResponseMetadata autoMd = ResponseMetadata.auto();
      assertEquals("v3.0", autoMd.getApiVersion());
      assertEquals("staging", autoMd.getEnvironment());
      assertTrue(autoMd.getDurationMs() >= 0);
   }

   // -------------------------------------------------------------
   // Nested error messages improvement
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should verify parent container nodes have null message in hierarchical errors")
   void testNestedErrorParentHasNullMessage() {
      List<FieldErrorDetail> flat = List.of(
            FieldErrorDetail.of("order.shipping.zipCode", "Invalid zip code"),
            FieldErrorDetail.of("order.shipping.country", "Country required"));

      List<FieldErrorDetail> tree = FieldErrorDetail.buildHierarchicalErrors(flat);
      assertEquals(1, tree.size());

      FieldErrorDetail orderErr = tree.get(0);
      assertEquals("order", orderErr.getField());
      assertNull(orderErr.getMessage(), "Parent container node should have null message");
      assertTrue(orderErr.hasNestedErrors());

      FieldErrorDetail shippingErr = orderErr.getNestedErrors().get(0);
      assertEquals("shipping", shippingErr.getField());
      assertNull(shippingErr.getMessage(), "Intermediate container node should have null message");
      assertTrue(shippingErr.hasNestedErrors());

      FieldErrorDetail zipErr = shippingErr.getNestedErrors().stream()
            .filter(e -> "zipCode".equals(e.getField()))
            .findFirst()
            .orElseThrow();
      assertEquals("Invalid zip code", zipErr.getMessage());
   }

   // -------------------------------------------------------------
   // Copy and Transform methods: map and toBuilder
   // -------------------------------------------------------------
   @Test
   @DisplayName("Should transform payload using map and copy using toBuilder")
   void testMapAndToBuilder() {
      BaseResponse<Integer> original = BaseResponse.<Integer>builder()
            .level(ResponseLevel.SUCCESS)
            .code("SUCCESS")
            .message("Created")
            .data(42)
            .pagination(PageInfo.of(0, 10, 1))
            .duration(12L)
            .apiVersion("v1.0")
            .environment("test")
            .addWarning("Warning 1")
            .path("/api/v1/calc")
            .build();

      // Test map
      BaseResponse<String> mapped = original.map(val -> "Result: " + val);
      assertEquals("Result: 42", mapped.getData());
      assertEquals(original.getCode(), mapped.getCode());
      assertEquals(original.getMessage(), mapped.getMessage());
      assertEquals(original.getTraceId(), mapped.getTraceId());
      assertEquals(original.getPath(), mapped.getPath());
      assertEquals(original.getPagination(), mapped.getPagination());
      assertEquals(original.getMetadata(), mapped.getMetadata());
      assertEquals(original.getWarnings(), mapped.getWarnings());

      // Test toBuilder
      BaseResponse<Integer> modified = original.toBuilder()
            .message("Updated message")
            .data(99)
            .build();

      assertEquals(99, modified.getData());
      assertEquals("Updated message", modified.getMessage());
      assertEquals(original.getCode(), modified.getCode());
      assertEquals(original.getTraceId(), modified.getTraceId());

      // Null mapper check
      assertThrows(IllegalArgumentException.class, () -> original.map(null));
   }
}
