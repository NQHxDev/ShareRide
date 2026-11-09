package com.shareride.common.outbox;

import com.shareride.common.metrics.ShareRideMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot AutoConfiguration for the Transactional Outbox Pattern:
 *
 * <ul>
 *   <li>Configures default publisher using in-memory Spring events
 *   <li>Configures default Dead Letter Queue handler with alerting and metrics
 *   <li>Configures outbox relay service for batch processing and retries
 *   <li>Configures background scheduler to poll and relay domain events
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(OutboxMessageRepository.class)
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "shareride.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OutboxPublisher.class)
    public OutboxPublisher defaultOutboxPublisher(
            ObjectProvider<ApplicationEventPublisher> applicationEventPublisherProvider) {
        return new DefaultOutboxPublisher(
                applicationEventPublisherProvider != null
                        ? applicationEventPublisherProvider.getIfAvailable()
                        : null);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxDeadLetterHandler.class)
    public OutboxDeadLetterHandler defaultOutboxDeadLetterHandler(
            ObjectProvider<ShareRideMetrics> metricsProvider,
            ObjectProvider<ApplicationEventPublisher> applicationEventPublisherProvider) {
        return new DefaultOutboxDeadLetterHandler(
                metricsProvider,
                applicationEventPublisherProvider != null
                        ? applicationEventPublisherProvider.getIfAvailable()
                        : null);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxRelayService.class)
    public OutboxRelayService outboxRelayService(
            OutboxMessageRepository outboxMessageRepository,
            OutboxPublisher outboxPublisher,
            OutboxDeadLetterHandler deadLetterHandler,
            OutboxProperties outboxProperties,
            ObjectProvider<ShareRideMetrics> metricsProvider) {
        return new OutboxRelayService(
                outboxMessageRepository,
                outboxPublisher,
                deadLetterHandler,
                outboxProperties,
                metricsProvider);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxRelayScheduler.class)
    @ConditionalOnProperty(
            prefix = "shareride.outbox.relay",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public OutboxRelayScheduler outboxRelayScheduler(OutboxRelayService outboxRelayService) {
        return new OutboxRelayScheduler(outboxRelayService);
    }
}
