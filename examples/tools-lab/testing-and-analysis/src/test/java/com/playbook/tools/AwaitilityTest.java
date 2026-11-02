package com.playbook.tools;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testing asynchronous code WITHOUT Thread.sleep: Awaitility polls a condition until it holds or a timeout expires.
 * Fast when the system is fast, tolerant when CI is slow, and the failure message says what never became true.
 */
class AwaitilityTest {

    @Test
    void orderIsEventuallyProcessed() {
        try (var processor = new OrderProcessor()) {
            processor.submit("order-1");
            processor.submit("order-2");

            await().atMost(Duration.ofSeconds(2))
                   .pollInterval(Duration.ofMillis(10))
                   .until(() -> processor.isProcessed("order-2"));

            await().atMost(Duration.ofSeconds(2))
                   .untilAsserted(() -> assertThat(processor.processedCount()).isEqualTo(2));
        }
    }

    @Test
    void stateStaysStableDuringAWindow() {
        try (var processor = new OrderProcessor()) {
            // nothing submitted: assert the count STAYS 0 for 200 ms (Awaitility "during")
            await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(1))
                   .until(() -> processor.processedCount() == 0);
        }
    }
}
