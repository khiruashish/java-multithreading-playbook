import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Ask N vendors for a quote in parallel; return the cheapest quote that arrives within a deadline, ignoring
 * vendors that fail or are too slow. Solved Java-8 style (CompletableFuture) and Java-21 style (virtual threads).
 * Run: java BestPriceFanOut.java
 */
public class BestPriceFanOut {

    record Quote(String vendor, long priceMinor) {}

    record Vendor(String name, long priceMinor, long latencyMs, boolean fails) {
        Quote quote() throws InterruptedException {
            Thread.sleep(latencyMs);
            if (fails) throw new IllegalStateException(name + " is down");
            return new Quote(name, priceMinor);
        }
    }

    // --8<-- [start:java8]
    static Optional<Quote> bestJava8(List<Vendor> vendors, Duration deadline, ExecutorService pool) {
        List<CompletableFuture<Optional<Quote>>> futures = vendors.stream()
                .map(v -> CompletableFuture
                        .supplyAsync(() -> {
                            try { return Optional.of(v.quote()); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); return Optional.<Quote>empty(); }
                        }, pool)
                        .completeOnTimeout(Optional.empty(), deadline.toMillis(), TimeUnit.MILLISECONDS) // too slow
                        .exceptionally(ex -> Optional.empty()))                                         // failed
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)                 // all done: join() won't block
                        .flatMap(Optional::stream)
                        .min(Comparator.comparingLong(Quote::priceMinor)))
                .join();
        // Note: completeOnTimeout does NOT stop the slow vendor call; its pool thread stays busy.
    }
    // --8<-- [end:java8]

    // --8<-- [start:java21]
    static Optional<Quote> bestJava21(List<Vendor> vendors, Duration deadline) throws InterruptedException {
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Quote>> calls = vendors.stream().<Callable<Quote>>map(v -> v::quote).toList();
            List<Future<Quote>> results = ex.invokeAll(calls, deadline.toMillis(), TimeUnit.MILLISECONDS);
            // invokeAll returns when all finished OR the deadline passed; unfinished ones are CANCELLED (interrupted)
            return results.stream()
                    .filter(f -> f.state() == Future.State.SUCCESS)   // skip FAILED and CANCELLED
                    .map(Future::resultNow)
                    .min(Comparator.comparingLong(Quote::priceMinor));
        }
    }
    // --8<-- [end:java21]

    public static void main(String[] args) throws Exception {
        List<Vendor> vendors = List.of(
                new Vendor("fresh-mart", 52_00, 80, false),
                new Vendor("green-grocer", 47_00, 120, false),
                new Vendor("cheap-but-slow", 39_00, 2_000, false),   // cheapest, but misses the deadline
                new Vendor("broken", 10_00, 30, true),               // fails
                new Vendor("daily-needs", 49_00, 60, false));
        Duration deadline = Duration.ofMillis(300);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        long t0 = System.nanoTime();
        Optional<Quote> j8 = bestJava8(vendors, deadline, pool);
        long j8ms = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdownNow();

        t0 = System.nanoTime();
        Optional<Quote> j21 = bestJava21(vendors, deadline);
        long j21ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("Java 8 : %s in %d ms%nJava 21: %s in %d ms%n", j8.orElseThrow(), j8ms, j21.orElseThrow(), j21ms);
        if (!j8.orElseThrow().vendor().equals("green-grocer") || !j21.equals(j8)) throw new AssertionError();
        if (j8ms > 1_000 || j21ms > 1_000) throw new AssertionError("deadline not honoured");
        System.out.println("PASS");
    }
}
