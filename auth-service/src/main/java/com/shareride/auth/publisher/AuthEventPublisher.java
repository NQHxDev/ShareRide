package com.shareride.auth.publisher;

import com.shareride.common.outbox.OutboxService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publisher cho các sự kiện xác thực và thông báo từ Auth Service. Hỗ trợ cả Transactional Outbox
 * pattern và async RabbitMQ publishing.
 *
 * <p>Transactional Outbox đảm bảo không có dual-write hazard bằng cách lưu event vào database trong
 * cùng transaction với domain changes.
 */
@Slf4j
@Service
public class AuthEventPublisher {

    private static final String EXCHANGE_NAME = "shareride.notification.exchange";
    private static final String ROUTING_KEY = "notification.email.dispatch";

    private final OutboxService outboxService;
    private final RabbitTemplate rabbitTemplate;

    public AuthEventPublisher(OutboxService outboxService, RabbitTemplate rabbitTemplate) {
        this.outboxService = outboxService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Xuất bản sự kiện xác thực email qua Transactional Outbox. Event được persist trong outbox
     * table cùng transaction với tài khoản và verification record.
     *
     * @param accountId ID tài khoản
     * @param eventType loại sự kiện (ACCOUNT_VERIFICATION, WELCOME)
     * @param payload nội dung sự kiện dạng Map
     */
    @Transactional
    public void publishAccountEvent(
            String accountId, String eventType, Map<String, Object> payload) {
        outboxService.recordEvent("Account", accountId, eventType, payload);

        log.info(
                "Recorded Account event via Outbox [accountId={}, eventType={}]",
                accountId,
                eventType);
    }

    /**
     * Xuất bản sự kiện xác thực email trực tiếp qua RabbitMQ (async, fire-and-forget). Sử dụng cho
     * các thông báo không quan trọng hoặc khi không yêu cầu đảm bảo transactional.
     *
     * @param payload nội dung sự kiện
     */
    public void publishDirect(Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, payload);

            log.info(
                    "Published event directly to RabbitMQ [exchange={}, routingKey={}]",
                    EXCHANGE_NAME,
                    ROUTING_KEY);

        } catch (Exception e) {
            log.error("Failed to publish event directly: {}", e.getMessage(), e);
            throw new RuntimeException("Direct event publishing failed", e);
        }
    }
}
