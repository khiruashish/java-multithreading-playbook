package com.playbook.vt.concurrency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FanOutTest {

    private final FanOut fanOut = new FanOut("test-fanout-");

    @Test
    void runsSubtasksConcurrentlyOnVirtualThreads() throws Exception {
        long start = System.nanoTime();
        try (var scope = fanOut.open(Duration.ofSeconds(2))) {
            var a = scope.fork(() -> { Thread.sleep(300); return Thread.currentThread().isVirtual(); });
            var b = scope.fork(() -> { Thread.sleep(300); return "b"; });
            var c = scope.fork(() -> { Thread.sleep(300); return "c"; });
            assertThat(a.get()).isTrue();
            assertThat(b.get() + c.get()).isEqualTo("bc");
        }
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat(ms).as("parallel, not 900ms sequential").isLessThan(800);
    }

    @Test
    void requiredFailureCancelsSiblings() throws Exception {
        var siblingInterrupted = new CountDownLatch(1);
        var siblingStarted = new CountDownLatch(1);

        assertThatThrownBy(() -> {
            try (var scope = fanOut.open(Duration.ofSeconds(5))) {
                scope.fork(() -> {
                    siblingStarted.countDown();
                    try {
                        Thread.sleep(Duration.ofSeconds(10));
                    } catch (InterruptedException e) {
                        siblingInterrupted.countDown();
                        throw e;
                    }
                    return "never";
                });
                var failing = scope.fork(() -> {
                    siblingStarted.await();
                    throw new IllegalArgumentException("boom");
                });
                failing.get();
            }
        }).isInstanceOf(IllegalArgumentException.class).hasMessage("boom");

        assertThat(siblingInterrupted.await(1, TimeUnit.SECONDS)).as("sibling was cancelled").isTrue();
    }

    @Test
    void optionalSubtaskDegradesOnDeadline() throws Exception {
        var interrupted = new AtomicBoolean();
        try (var scope = fanOut.open(Duration.ofMillis(200))) {
            var slow = scope.forkOptional(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(5));
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    throw e;
                }
                return "slow";
            }, ex -> "fallback");
            assertThat(slow.get()).isEqualTo("fallback");
            assertThat(slow.degraded()).isTrue();
        }
        assertThat(interrupted).as("timed-out subtask was interrupted before scope closed").isTrue();
    }

    @Test
    void requiredSubtaskPastDeadlineThrowsDeadlineExceeded() {
        assertThatThrownBy(() -> {
            try (var scope = fanOut.open(Duration.ofMillis(100))) {
                scope.fork(() -> { Thread.sleep(Duration.ofSeconds(5)); return "late"; }).get();
            }
        }).isInstanceOf(DeadlineExceededException.class);
    }
}
