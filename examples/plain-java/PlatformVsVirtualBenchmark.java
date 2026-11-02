import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Run: java PlatformVsVirtualBenchmark.java
 * Simulates IO-bound work (sleep = waiting on a DB/HTTP call). Not a JMH benchmark; it shows the order of magnitude.
 */
public class PlatformVsVirtualBenchmark {

    static long run(ExecutorService ex, int tasks, Duration io) {
        long t0 = System.nanoTime();
        try (ex) {
            for (int i = 0; i < tasks; i++) {
                ex.submit(() -> {
                    Thread.sleep(io);
                    return null;
                });
            }
        }
        return (System.nanoTime() - t0) / 1_000_000;
    }

    public static void main(String[] args) {
        int tasks = 10_000;
        Duration io = Duration.ofMillis(100);
        System.out.printf("%,d tasks x %d ms simulated IO%n", tasks, io.toMillis());
        System.out.printf("  Java 8 style  fixed pool(200)          : %,6d ms%n",
                run(Executors.newFixedThreadPool(200), tasks, io));
        System.out.printf("  Java 8 style  fixed pool(1000)         : %,6d ms%n",
                run(Executors.newFixedThreadPool(1000), tasks, io));
        System.out.printf("  Java 21       virtual thread per task  : %,6d ms%n",
                run(Executors.newVirtualThreadPerTaskExecutor(), tasks, io));
    }
}
