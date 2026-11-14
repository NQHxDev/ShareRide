package com.shareride.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.shareride.notification.domain.NotificationType;
import com.shareride.notification.service.impl.EmailTemplateRendererImpl;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class EmailTemplateRendererTest {

    @Mock private EmailTemplateRegistry templateRegistry;

    @Mock private EmailTemplateStrategy mockStrategy;

    @Mock private MessageSource messageSource;

    private EmailTemplateRendererImpl templateRenderer;

    private Configuration freemarkerConfiguration;

    @BeforeEach
    void setUp() {
        freemarkerConfiguration = new Configuration(Configuration.VERSION_2_3_34);
        freemarkerConfiguration.setClassLoaderForTemplateLoading(
                getClass().getClassLoader(), "templates/email");
        freemarkerConfiguration.setDefaultEncoding("UTF-8");
        freemarkerConfiguration.setOutputFormat(freemarker.core.HTMLOutputFormat.INSTANCE);
        freemarkerConfiguration.setTemplateExceptionHandler(
                TemplateExceptionHandler.RETHROW_HANDLER);
        freemarkerConfiguration.setLogTemplateExceptions(false);
        freemarkerConfiguration.setWrapUncheckedExceptions(true);
        freemarkerConfiguration.setFallbackOnNullLoopVariable(false);

        templateRenderer =
                new EmailTemplateRendererImpl(
                        freemarkerConfiguration, templateRegistry, messageSource);

        lenient()
                .when(templateRegistry.getStrategy(any(NotificationType.class)))
                .thenReturn(mockStrategy);
        lenient().when(mockStrategy.getSubjectKey()).thenReturn("Test Subject");
        lenient().when(mockStrategy.getHtmlTemplatePath()).thenReturn("test/test_body.ftl");
        lenient()
                .when(mockStrategy.getPlainTextTemplatePath())
                .thenReturn("test/test_body_text.ftl");

        // Mock message source for layout templates
        lenient()
                .when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(
                        invocation -> {
                            String key = invocation.getArgument(0);
                            return "Mocked: " + key;
                        });
    }

    @Test
    void shouldRenderEmailWithLayoutComponents() {
        Map<String, Object> model = new HashMap<>();
        model.put("userName", "Nguyễn Văn A");
        model.put("verificationCode", "123456");
        model.put("expiresInMinutes", 15);

        when(mockStrategy.enrichModel(any(), any())).thenReturn(model);

        String html =
                templateRenderer.renderHtml(
                        NotificationType.ACCOUNT_VERIFICATION, Locale.forLanguageTag("vi"), model);

        assertThat(html).contains("ShareRide");
        assertThat(html).contains("Nguyễn Văn A");
        assertThat(html).contains("123456");
        assertThat(html).doesNotContain("${");
    }

    @Test
    void shouldEscapeHtmlAndPreserveVietnamese() {
        Map<String, Object> model = new HashMap<>();
        model.put("userName", "Nguyễn <script>alert('xss')</script>");
        model.put("verificationCode", "999999");
        model.put("expiresInMinutes", 10);

        when(mockStrategy.enrichModel(any(), any())).thenReturn(model);

        String html =
                templateRenderer.renderHtml(
                        NotificationType.ACCOUNT_VERIFICATION, Locale.forLanguageTag("vi"), model);

        assertThat(html).contains("Nguyễn");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).containsAnyOf("&lt;script&gt;", "&amp;lt;script&amp;gt;");
    }

    @Test
    void shouldRenderPlainTextAlternative() {
        Map<String, Object> model = new HashMap<>();
        model.put("userName", "Test User");
        model.put("verificationCode", "654321");
        model.put("expiresInMinutes", 15);

        when(mockStrategy.enrichModel(any(), any())).thenReturn(model);

        String plainText =
                templateRenderer.renderPlainText(
                        NotificationType.ACCOUNT_VERIFICATION, Locale.forLanguageTag("en"), model);

        assertThat(plainText).contains("Test User");
        assertThat(plainText).contains("654321");
        assertThat(plainText).doesNotContain("<");
        assertThat(plainText).doesNotContain("${");
    }

    @Test
    void shouldRenderFullEmailWithBothHtmlAndPlainText() {
        Map<String, Object> model = new HashMap<>();
        model.put("userName", "Test User");
        model.put("verificationCode", "654321");
        model.put("expiresInMinutes", 15);

        when(mockStrategy.enrichModel(any(), any())).thenReturn(model);

        com.shareride.notification.dto.RenderedEmail email =
                templateRenderer.render(
                        NotificationType.ACCOUNT_VERIFICATION, Locale.forLanguageTag("en"), model);

        assertThat(email).isNotNull();
        assertThat(email.getHtmlBody()).contains("Test User");
        assertThat(email.getPlainTextBody()).contains("Test User");
        assertThat(email.getSubject()).isEqualTo("Test Subject");
    }

    @Test
    void shouldThrowRuntimeExceptionWhenRenderHtmlFails() {
        when(mockStrategy.getHtmlTemplatePath()).thenReturn("non_existent_template.ftl");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                templateRenderer.renderHtml(
                                        NotificationType.ACCOUNT_VERIFICATION,
                                        Locale.ENGLISH,
                                        Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to render HTML template");
    }

    @Test
    void shouldThrowRuntimeExceptionWhenRenderPlainTextFails() {
        when(mockStrategy.getPlainTextTemplatePath()).thenReturn("non_existent_text_template.ftl");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                templateRenderer.renderPlainText(
                                        NotificationType.ACCOUNT_VERIFICATION,
                                        Locale.ENGLISH,
                                        Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to render plain text template");
    }
}
