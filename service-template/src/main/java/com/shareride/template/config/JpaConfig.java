package com.shareride.template.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Standard JPA configuration for Template Service. Scans entities and repositories across
 * 'com.shareride' to support common outbox and base entities.
 */
@Configuration
@EntityScan(basePackages = "com.shareride")
@EnableJpaRepositories(basePackages = "com.shareride")
public class JpaConfig {}
