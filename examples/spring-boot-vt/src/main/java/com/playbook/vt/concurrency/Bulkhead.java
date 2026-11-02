package com.playbook.vt.concurrency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Semaphore bulkhead: THE way to limit concurrency once threads are virtual.
 *
 * <p>With platform threads the pool size implicitly capped calls to a dependency. With virtual threads there is no
 * such cap, so every dependency gets an explicit one. Callers that cannot get a permit within {@code maxWait} fail
 * fast instead of queuing forever, which isolates a slow dependency from the rest of the service.
 */
public final class Bulkhead {

    private final String name;
    private final int maxConcurrent;
    private final Semaphore permits;
    private final Duration maxWait;
    private final Counter rejected;
    private final Timer waitTimer;

    public Bulkhead(String name, int maxConcurrent, Duration maxWait, MeterRegistry registry) {
        if (maxConcurrent <= 0) throw new IllegalArgumentException("maxConcurrent must be > 0");
        this.name = name;
        this.maxConcurrent = maxConcurrent;
        this.permits = new Semaphore(maxConcurrent, true);
        this.maxWait = maxWait;
        this.rejected = Counter.builder("bulkhead.rejected").tag("name", name).register(registry);
        this.waitTimer = Timer.builder("bulkhead.wait").tag("name", name).register(registry);
        Gauge.builder("bulkhead.in_use", this, Bulkhead::inUse).tag("name", name).register(registry);
    }

    public <T> T call(Callable<T> task) throws Exception {
        long start = System.nanoTime();
        boolean acquired = permits.tryAcquire(maxWait.toNanos(), TimeUnit.NANOSECONDS); // parks: VT unmounts
        waitTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        if (!acquired) {
            rejected.increment();
            throw new BulkheadFullException(name);
        }
        try {
            return task.call();
        } finally {
            permits.release();
        }
    }

    public int inUse() {
        return maxConcurrent - permits.availablePermits();
    }

    public String name() {
        return name;
    }
}
