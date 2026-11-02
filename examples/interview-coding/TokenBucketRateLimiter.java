import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token-bucket rate limiter: capacity B (burst), refill R tokens/second. tryAcquire() never blocks; acquire() waits.
 * Run: java TokenBucketRateLimiter.java
 */
public class TokenBucketRateLimiter {

    // --8<-- [start:core]
    private final long capacity;
    private final double refillPerNano;
    private final ReentrantLock lock = new ReentrantLock();
    private double tokens;                            // guarded by lock
    private long lastRefillNanos;                     // guarded by lock

    public TokenBucketRateLimiter(long capacity, long tokensPerSecond) {
        this.capacity = capacity;
        this.refillPerNano = tokensPerSecond / 1_000_000_000.0;
        this.tokens = capacity;                       // start full → allows an initial burst
        this.lastRefillNanos = System.nanoTime();
    }

    /** Lazy refill: no background thread; tokens are computed from elapsed time on each call. */
    private void refill(long now) {
        tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * refillPerNano);
        lastRefillNanos = now;
    }

    public boolean tryAcquire() {
        lock.lock();
        try {
            refill(System.nanoTime());
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void acquire() throws InterruptedException {
        while (true) {
            long waitNanos;
            lock.lock();
            try {
                refill(System.nanoTime());
                if (tokens >= 1) {
                    tokens -= 1;
                    return;
                }
                waitNanos = (long) Math.ceil((1 - tokens) / refillPerNano);   // time until 1 token exists
            } finally {
                lock.unlock();
            }
            Thread.sleep(Duration.ofNanos(waitNanos));   // sleep OUTSIDE the lock; cheap on a virtual thread
        }
    }
    // --8<-- [end:core]

    public static void main(String[] args) throws Exception {
        var limiter = new TokenBucketRateLimiter(10, 100);       // burst 10, 100/s
        AtomicInteger allowed = new AtomicInteger();
        long end = System.nanoTime() + Duration.ofMillis(500).toNanos();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int c = 0; c < 50; c++) {                        // 50 greedy clients
                ex.submit(() -> { while (System.nanoTime() < end) { if (limiter.tryAcquire()) allowed.incrementAndGet(); Thread.onSpinWait(); } });
            }
        }
        // expected ≈ burst 10 + 0.5 s x 100/s = 60
        System.out.println("tryAcquire: 50 greedy clients for 500 ms -> allowed " + allowed.get() + " (expected ~60)");
        if (allowed.get() < 50 || allowed.get() > 70) throw new AssertionError();

        var blocking = new TokenBucketRateLimiter(1, 200);       // no burst, 200/s
        long t0 = System.nanoTime();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 101; i++) ex.submit(() -> { blocking.acquire(); return null; });
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("acquire: 101 permits at 200/s took " + ms + " ms (expected ~500)");
        if (ms < 400 || ms > 1_500) throw new AssertionError();
        System.out.println("PASS");
    }
}
