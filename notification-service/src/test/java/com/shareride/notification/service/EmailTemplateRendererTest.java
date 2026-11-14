package com.shareride.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.notification.domain.NotificationType;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailTemplateRendererTest {

    @Mock private EmailTemplateRegistry templateRegistry;

    @InjectMocks private EmailTemplateRendererImpl templateRenderer;

    @Test
    void shouldRenderEmailWithLayoutComponents() {
        Map<String, Object> model = new HashMap<>();
        model.put("userName", "Nguyễn Văn A");
        model.put("verificationCode", "123456");
        model.put("expiresInMinutes", 15);

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

        String plainText =
                templateRenderer.renderPlainText(
                        NotificationType.ACCOUNT_VERIFICATION, Locale.forLanguageTag("en"), model);

        assertThat(plainText).contains("Test User");
        assertThat(plainText).contains("654321");
        assertThat(plainText).doesNotContain("<");
        assertThat(plainText).doesNotContain("${");
    }
}
