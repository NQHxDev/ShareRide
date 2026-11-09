package com.shareride.template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for ShareRide Template Microservice. Scans 'com.shareride' package to auto-configure
 * all common enterprise capabilities.
 */
@SpringBootApplication(scanBasePackages = "com.shareride")
public class TemplateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateServiceApplication.class, args);
    }
}
