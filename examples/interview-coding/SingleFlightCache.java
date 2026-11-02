import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Request coalescing ("single flight"): when 1,000 threads ask for the same missing key at once, the expensive
 * loader runs ONCE and everyone shares the result. Prevents cache stampedes / thundering herds.
 * Run: java SingleFlightCache.java
 */
public class SingleFlightCache<K, V> {

    // --8<-- [start:core]
    private final ConcurrentHashMap<K, CompletableFuture<V>> cache = new ConcurrentHashMap<>();
    private final Function<K, V> loader;

    public SingleFlightCache(Function<K, V> loader) { this.loader = loader; }

    public V get(K key) throws InterruptedException, ExecutionException {
        CompletableFuture<V> mine = new CompletableFuture<>();
        CompletableFuture<V> existing = cache.putIfAbsent(key, mine);   // atomic: exactly one thread wins
        if (existing != null) {
            return existing.get();                                       // losers wait for the winner's result
        }
        try {                                                            // winner loads OUTSIDE any map lock
            V value = loader.apply(key);
            mine.complete(value);
            return value;
        } catch (RuntimeException e) {
            cache.remove(key, mine);                                     // don't cache failures: allow a retry
            mine.completeExceptionally(e);                               // ...but fail everyone currently waiting
            throw e;
        }
    }
    // --8<-- [end:core]

    public static void main(String[] args) throws Exception {
        AtomicInteger loads = new AtomicInteger();
        var cache = new SingleFlightCache<String, String>(key -> {
            loads.incrementAndGet();
            try { Thread.sleep(200); } catch (InterruptedException e) { throw new RuntimeException(e); }   // slow DB
            return key.toUpperCase();
        });
        AtomicInteger correct = new AtomicInteger();
        long t0 = System.nanoTime();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1_000; i++) {
                String key = "sku-" + (i % 3);                           // 3 distinct keys
                ex.submit(() -> { if (cache.get(key).equals(key.toUpperCase())) correct.incrementAndGet(); return null; });
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("1,000 concurrent gets on 3 keys: loader calls=%d, correct=%d, took %d ms (one 200 ms load per key, in parallel)%n",
                loads.get(), correct.get(), ms);
        if (loads.get() != 3 || correct.get() != 1_000) throw new AssertionError();

        AtomicInteger attempts = new AtomicInteger();
        var flaky = new SingleFlightCache<String, String>(k -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("db down");
            return "ok";
        });
        try { flaky.get("x"); } catch (IllegalStateException expected) { /* first call fails */ }
        if (!"ok".equals(flaky.get("x"))) throw new AssertionError("failure must not be cached");
        System.out.println("failed load not cached: retry succeeded");
        System.out.println("PASS");
    }
}
