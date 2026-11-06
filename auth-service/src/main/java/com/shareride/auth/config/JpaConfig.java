package com.shareride.auth.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.shareride")
@EnableJpaRepositories(basePackages = "com.shareride")
public class JpaConfig {
}
