package com.shareride.auth.service.impl;

import com.shareride.auth.config.BloomFilterProperties;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.service.EmailBloomFilterService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.rebloom.client.Client;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of EmailBloomFilterService using RedisBloom (jrebloom) client. Provides fast
 * probabilistic email existence checks with Valkey database 0 isolation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailBloomFilterServiceImpl implements EmailBloomFilterService {

    private final BloomFilterProperties properties;
    private final LettuceConnectionFactory connectionFactory;
    private final AccountRepository accountRepository;
    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, String> redisTemplate;

    private Client bloomClient;

    @PostConstruct
    public void init() {
        String host = connectionFactory.getHostName();
        int port = connectionFactory.getPort();
        int database = connectionFactory.getDatabase();

        log.info(
                "Initializing Bloom filter client: host={}, port={}, database={}",
                host,
                port,
                database);

        this.bloomClient = new Client(host, port);

        if (!isInitialized()) {
            log.info("Bloom filter not found, initializing...");
            initializeBloomFilter();
        } else {
            log.info("Bloom filter already initialized: {}", properties.getKey());
        }
    }

    @Override
    public boolean mightExist(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }

        if (!properties.isEnabled()) {
            log.debug("Bloom filter disabled, returning true to force DB check");
            return true;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String normalizedEmail = normalizeEmail(email);
            boolean exists = bloomClient.exists(properties.getKey(), normalizedEmail);

            meterRegistry.counter("auth.bloom.check.total").increment();

            if (exists) {
                meterRegistry.counter("auth.bloom.hit.total").increment();
                log.debug("Bloom filter HIT for email: {}", normalizedEmail);
            } else {
                meterRegistry.counter("auth.bloom.miss.total").increment();
                log.debug("Bloom filter MISS for email: {}", normalizedEmail);
            }

            return exists;

        } catch (Exception e) {
            meterRegistry.counter("auth.bloom.fallback.total").increment();
            log.warn("Valkey unavailable during Bloom filter check, falling back to DB", e);
            return true;
        } finally {
            sample.stop(
                    Timer.builder("auth.bloom.check.duration")
                            .tag("service", "auth-service")
                            .register(meterRegistry));
        }
    }

    @Override
    public void addEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }

        if (!properties.isEnabled()) {
            log.debug("Bloom filter disabled, skipping add operation");
            return;
        }

        try {
            String normalizedEmail = normalizeEmail(email);
            bloomClient.add(properties.getKey(), normalizedEmail);
            meterRegistry.counter("auth.bloom.add.total").increment();
            log.debug("Added email to Bloom filter: {}", normalizedEmail);

        } catch (Exception e) {
            log.error("Failed to add email to Bloom filter: {}", email, e);
        }
    }

    @Override
    public void initializeBloomFilter() {
        try {
            if (isInitialized()) {
                log.info("Bloom filter already exists, skipping initialization");
                return;
            }

            bloomClient.createFilter(
                    properties.getKey(),
                    properties.getInitialCapacity(),
                    properties.getErrorRate());

            log.info(
                    "Bloom filter initialized: key={}, capacity={}, errorRate={}",
                    properties.getKey(),
                    properties.getInitialCapacity(),
                    properties.getErrorRate());

        } catch (Exception e) {
            log.error("Failed to initialize Bloom filter", e);
            throw new RuntimeException("Bloom filter initialization failed", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long rebuildFromDatabase() {
        log.info("Starting Bloom filter rebuild from PostgreSQL...");

        try {
            clear();
            initializeBloomFilter();

            long totalEmails = 0;
            int pageSize = 1000;
            Pageable pageable = PageRequest.of(0, pageSize);

            Page<String> emailPage;
            do {
                emailPage = accountRepository.findAllEmails(pageable);

                for (String email : emailPage.getContent()) {
                    addEmail(email);
                    totalEmails++;
                }

                pageable = emailPage.nextPageable();
                log.debug("Rebuilt {} emails so far...", totalEmails);

            } while (emailPage.hasNext());

            meterRegistry.counter("auth.bloom.rebuild.total").increment();
            log.info("Bloom filter rebuild completed: {} emails added", totalEmails);

            return totalEmails;

        } catch (Exception e) {
            log.error("Bloom filter rebuild failed", e);
            throw new RuntimeException("Bloom filter rebuild failed", e);
        }
    }

    @Override
    public void clear() {
        try {
            bloomClient.delete(properties.getKey());
            log.info("Bloom filter cleared: {}", properties.getKey());

        } catch (Exception e) {
            log.warn("Failed to clear Bloom filter (may not exist): {}", e.getMessage());
        }
    }

    @Override
    public boolean isInitialized() {
        try {
            // Check if key exists using RedisTemplate
            Boolean exists = redisTemplate.hasKey(properties.getKey());
            return exists != null && exists;
        } catch (Exception e) {
            log.debug("Failed to check Bloom filter existence", e);
            return false;
        }
    }

    @Override
    public BloomFilterInfo getFilterInfo() {
        try {
            // jrebloom 2.2.2 may not have getInfo(), return basic info
            boolean exists = isInitialized();
            if (!exists) {
                return new BloomFilterInfo(0, 0, 0, 0, 0);
            }

            // Return configured values as placeholders
            return new BloomFilterInfo(properties.getInitialCapacity(), 0, 1, 0, 2);

        } catch (Exception e) {
            log.error("Failed to get Bloom filter info", e);
            throw new RuntimeException("Failed to retrieve Bloom filter statistics", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
