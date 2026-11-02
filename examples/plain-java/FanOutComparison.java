import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Run: java FanOutComparison.java
 * The same 3-way fan-out (product, price, stock) written Java 8 style and Java 21 style, then load-tested with
 * 2,000 concurrent "requests".
 */
public class FanOutComparison {

    record Page(String product, String price, String stock) {}

    static String call(String what, long ms) {
        try {
            Thread.sleep(ms);                       // simulated blocking IO
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return what;
    }

    // ---------- Java 8: CompletableFuture on a sized platform pool ----------
    static final ExecutorService IO_POOL = Executors.newFixedThreadPool(200);

    static Page java8(String id) {
        CompletableFuture<String> product = CompletableFuture.supplyAsync(() -> call("product-" + id, 120), IO_POOL);
        CompletableFuture<String> price = CompletableFuture.supplyAsync(() -> call("price", 80), IO_POOL)
                .completeOnTimeout("price-unknown", 300, TimeUnit.MILLISECONDS);
        CompletableFuture<String> stock = CompletableFuture.supplyAsync(() -> call("stock", 60), IO_POOL)
                .exceptionally(ex -> "stock-unknown");
        return product.thenCombine(price, (p, pr) -> new String[]{p, pr})
                .thenCombine(stock, (arr, s) -> new Page(arr[0], arr[1], s))
                .join();
    }

    // ---------- Java 21: plain blocking code on virtual threads ----------
    static Page java21(String id) throws Exception {
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> product = ex.submit(() -> call("product-" + id, 120));
            Future<String> price = ex.submit(() -> call("price", 80));
            Future<String> stock = ex.submit(() -> call("stock", 60));
            return new Page(product.get(), price.get(), stock.get());
        }
    }

    interface Handler { Page handle(String id) throws Exception; }

    static long load(ExecutorService requestThreads, Handler handler, int requests) {
        long t0 = System.nanoTime();
        try (requestThreads) {
            for (int i = 0; i < requests; i++) {
                String id = "p" + i;
                requestThreads.submit(() -> handler.handle(id));
            }
        }
        return (System.nanoTime() - t0) / 1_000_000;
    }

    public static void main(String[] args) {
        int requests = 2_000;
        System.out.println("single request, Java 8 : " + java8("1"));
        long j8 = load(Executors.newFixedThreadPool(200), FanOutComparison::java8, requests);   // Tomcat-like 200 threads
        long j21 = load(Executors.newVirtualThreadPerTaskExecutor(), FanOutComparison::java21, requests);
        IO_POOL.shutdown();
        System.out.printf("%,d concurrent requests x 3-way fan-out (max leg 120 ms)%n", requests);
        System.out.printf("  Java 8  (200 request threads + 200 IO pool, CompletableFuture): %,6d ms%n", j8);
        System.out.printf("  Java 21 (virtual thread per request + per subtask)          : %,6d ms%n", j21);
    }
}
