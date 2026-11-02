import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A deliberately misbehaving process to practise every concurrency tool on. It runs until killed and contains:
 *   1. a DEADLOCK between two platform threads ("payment-worker" / "refund-worker")
 *   2. LOCK CONTENTION: 8 platform threads fighting over one monitor ("hot-lock-N")
 *   3. PINNED virtual threads: blocking sleep inside synchronized (JDK 21-23 pins; JDK 24+ doesn't).
 *      Default 2. Pass a number >= #cores (e.g. "4" on 4 cores) to reproduce CARRIER STARVATION on JDK 21:
 *      every carrier is pinned and the other 10,000 virtual threads never get to run.
 *   4. 10,000 PARKED virtual threads waiting on a tiny "connection pool" (Semaphore(5)) -> pool exhaustion
 *   5. a CPU hog ("cpu-burner") for CPU profiling
 * Run: java -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints TroubledService.java [pinnedThreads]
 */
public class TroubledService {

    static final Object ACCOUNTS = new Object();
    static final Object LEDGER = new Object();
    static final Object HOT_LOCK = new Object();
    static final Semaphore CONNECTION_POOL = new Semaphore(5);
    static final AtomicLong hotCounter = new AtomicLong();

    public static void main(String[] args) throws Exception {
        int pinned = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        System.out.println("pid=" + ProcessHandle.current().pid() + " pinnedThreads=" + pinned
                + " carriers=" + Runtime.getRuntime().availableProcessors());

        // 1. deadlock
        CountDownLatch both = new CountDownLatch(2);
        Thread.ofPlatform().name("payment-worker").start(() -> {
            synchronized (ACCOUNTS) { both.countDown(); await(both); synchronized (LEDGER) { } }
        });
        Thread.ofPlatform().name("refund-worker").start(() -> {
            synchronized (LEDGER) { both.countDown(); await(both); synchronized (ACCOUNTS) { } }
        });

        // 2. contention
        for (int i = 0; i < 8; i++) {
            Thread.ofPlatform().name("hot-lock-" + i).start(() -> {
                while (true) {
                    synchronized (HOT_LOCK) {
                        long x = 0;
                        for (int k = 0; k < 20_000; k++) x += k;   // hold the lock doing work
                        hotCounter.addAndGet(x);
                    }
                }
            });
        }

        // 3. pinned virtual threads (sleep while holding a monitor)
        var vts = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < pinned; i++) {
            Object lock = new Object();
            vts.submit(() -> {
                while (true) {
                    synchronized (lock) { Thread.sleep(Duration.ofMillis(50)); }   // pins on JDK 21-23
                }
            });
        }

        // 4. pool exhaustion: 10k VTs, 5 permits, each holder "queries the DB" for 2 s
        for (int i = 0; i < 10_000; i++) {
            vts.submit(() -> {
                CONNECTION_POOL.acquire();
                try { slowQuery(); } finally { CONNECTION_POOL.release(); }
                return null;
            });
        }

        // 5. CPU hog
        Thread.ofPlatform().name("cpu-burner").start(() -> {
            double d = 0;
            while (true) d += Math.sqrt(System.nanoTime() % 1000);
        });

        Thread.sleep(Long.MAX_VALUE);
    }

    static void slowQuery() throws InterruptedException { TimeUnit.SECONDS.sleep(2); }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
