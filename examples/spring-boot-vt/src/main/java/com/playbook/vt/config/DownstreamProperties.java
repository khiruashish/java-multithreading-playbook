package com.playbook.vt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param baseUrl    base URL of the downstream services
 * @param selfHosted when true, the sample calls its own /sim endpoints and the base URL is resolved once the embedded
 *                   server knows its (possibly random) port
 */
@ConfigurationProperties("playbook.downstream")
public record DownstreamProperties(String baseUrl, boolean selfHosted, Duration connectTimeout, Duration readTimeout) {}
