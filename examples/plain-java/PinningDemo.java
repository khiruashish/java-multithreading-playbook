import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Run on JDK 21-23:  java -Djdk.virtualThreadScheduler.parallelism=2 PinningDemo.java
 * Run on JDK 24+:    same command — the synchronized case is now as fast as ReentrantLock (JEP 491).
 *
 * Each task blocks for 100 ms while holding its OWN lock (no contention), so the only thing that can serialize the
 * tasks is pinning. With 2 carriers and pinning, 20 tasks take ~1 s; without pinning, ~100 ms.
 */
public class PinningDemo {

    static long time(Runnable body) {
        long t0 = System.nanoTime();
        body.run();
        return (System.nanoTime() - t0) / 1_000_000;
    }

    public static void main(String[] args) {
        System.out.println("JDK " + Runtime.version().feature() + ", carriers="
                + System.getProperty("jdk.virtualThreadScheduler.parallelism", "#cores"));

        long sync = time(() -> {
            try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 20; i++) {
                    Object lock = new Object();
                    ex.submit(() -> {
                        synchronized (lock) {                       // pins on JDK 21-23
                            Thread.sleep(Duration.ofMillis(100));
                        }
                        return null;
                    });
                }
            }
        });

        long reentrant = time(() -> {
            try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 20; i++) {
                    ReentrantLock lock = new ReentrantLock();
                    ex.submit(() -> {
                        lock.lock();                                // never pins
                        try {
                            Thread.sleep(Duration.ofMillis(100));
                        } finally {
                            lock.unlock();
                        }
                        return null;
                    });
                }
            }
        });

        System.out.printf("synchronized + sleep : %,5d ms%n", sync);
        System.out.printf("ReentrantLock + sleep: %,5d ms%n", reentrant);
    }
}
