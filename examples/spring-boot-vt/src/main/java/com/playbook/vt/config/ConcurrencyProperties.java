package com.playbook.vt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties("playbook.concurrency")
public record ConcurrencyProperties(
        Admission admission,
        Duration fanOutTimeout,
        int cpuPoolSize,
        Map<String, BulkheadConfig> bulkheads) {

    public ConcurrencyProperties {
        if (admission == null) admission = new Admission(5_000);
        if (fanOutTimeout == null) fanOutTimeout = Duration.ofMillis(800);
        if (bulkheads == null) bulkheads = Map.of();
    }

    public record Admission(int maxInFlight) {}

    public record BulkheadConfig(int maxConcurrent, Duration maxWait) {}
}
