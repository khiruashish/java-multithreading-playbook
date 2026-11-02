package com.playbook.vt.concurrency;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BulkheadTest {

    @Test
    void neverExceedsMaxConcurrentEvenWithThousandsOfVirtualThreads() throws Exception {
        var bulkhead = new Bulkhead("test", 10, Duration.ofSeconds(30), new SimpleMeterRegistry());
        var current = new AtomicInteger();
        var peak = new AtomicInteger();

        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 2_000; i++) {
                ex.submit(() -> bulkhead.call(() -> {
                    int now = current.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);
                    Thread.sleep(Duration.ofMillis(2));
                    current.decrementAndGet();
                    return null;
                }));
            }
        }

        assertThat(peak.get()).isLessThanOrEqualTo(10).isGreaterThan(1);
        assertThat(bulkhead.inUse()).isZero();
    }

    @Test
    void rejectsFastWhenFull() throws Exception {
        var bulkhead = new Bulkhead("tiny", 1, Duration.ofMillis(20), new SimpleMeterRegistry());
        List<Future<Object>> results = new ArrayList<>();

        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                results.add(ex.submit(() -> bulkhead.call(() -> {
                    Thread.sleep(Duration.ofMillis(300));
                    return "ok";
                })));
            }
        }

        long rejected = results.stream().filter(f -> {
            try {
                f.get();
                return false;
            } catch (ExecutionException e) {
                return e.getCause() instanceof BulkheadFullException;
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }).count();
        assertThat(rejected).isEqualTo(4);
    }
}
