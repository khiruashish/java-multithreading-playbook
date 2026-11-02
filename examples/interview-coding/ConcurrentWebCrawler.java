import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LeetCode 1242 (web crawler multithreaded), production-flavoured: one virtual thread per page, a global limit on
 * in-flight fetches, a per-host politeness limit, a concurrent visited set, and a Phaser to know when we're done.
 * The "internet" is a simulated link graph where each fetch sleeps 5-15 ms.
 * Run: java ConcurrentWebCrawler.java
 */
public class ConcurrentWebCrawler {

    interface Fetcher { List<String> links(String url) throws InterruptedException; }

    // --8<-- [start:core]
    private final Fetcher fetcher;
    private final Set<String> visited = ConcurrentHashMap.newKeySet();      // atomic "claim this URL"
    private final Semaphore globalInFlight;                                 // protects us and the network
    private final Map<String, Semaphore> perHost = new ConcurrentHashMap<>();
    private final int perHostLimit;
    private final AtomicInteger peakInFlight = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();

    ConcurrentWebCrawler(Fetcher fetcher, int maxInFlight, int perHostLimit) {
        this.fetcher = fetcher;
        this.globalInFlight = new Semaphore(maxInFlight);
        this.perHostLimit = perHostLimit;
    }

    Set<String> crawl(String startUrl) {
        Phaser pending = new Phaser(1);                    // party #1 = the caller
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            submit(startUrl, ex, pending);
            pending.arriveAndAwaitAdvance();               // returns when every registered page has arrived
        }
        return visited;
    }

    private void submit(String url, ExecutorService ex, Phaser pending) {
        if (!visited.add(url)) return;                     // someone else already claimed it
        pending.register();                                // one more page in flight (BEFORE submitting!)
        ex.submit(() -> {
            try {
                for (String link : fetch(url)) {
                    if (host(link).equals(host(url))) submit(link, ex, pending);   // stay on the start host
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pending.arriveAndDeregister();             // this page is done
            }
        });
    }

    private List<String> fetch(String url) throws InterruptedException {
        Semaphore host = perHost.computeIfAbsent(host(url), h -> new Semaphore(perHostLimit));
        globalInFlight.acquire();
        try {
            host.acquire();
            try {
                peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                return fetcher.links(url);                 // blocking IO: the virtual thread parks
            } finally {
                inFlight.decrementAndGet();
                host.release();
            }
        } finally {
            globalInFlight.release();
        }
    }

    static String host(String url) { return url.split("/")[2]; }
    // --8<-- [end:core]

    public static void main(String[] args) {
        int pages = 2_000;
        Random rnd = new Random(42);
        Map<String, List<String>> graph = new HashMap<>();
        for (int i = 0; i < pages; i++) {
            List<String> out = new ArrayList<>();
            for (int k = 0; k < 4; k++) out.add("http://shop.test/p/" + rnd.nextInt(pages));
            out.add("http://other.test/p/" + i);           // off-host links must be ignored
            graph.put("http://shop.test/p/" + i, out);
        }
        Fetcher fetcher = url -> {
            Thread.sleep(Duration.ofMillis(5 + Math.floorMod(url.hashCode(), 11)));   // 5-15 ms of "network"
            return graph.getOrDefault(url, List.of());
        };

        // expected = everything reachable from page 0 (plain BFS)
        Set<String> expected = new HashSet<>();
        var stack = new ArrayList<String>(List.of("http://shop.test/p/0"));
        while (!stack.isEmpty()) {
            String u = stack.removeLast();
            if (!expected.add(u)) continue;
            for (String l : graph.getOrDefault(u, List.of())) if (host(l).equals("shop.test")) stack.add(l);
        }

        var crawler = new ConcurrentWebCrawler(fetcher, 64, 64);
        long t0 = System.nanoTime();
        Set<String> found = crawler.crawl("http://shop.test/p/0");
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("crawled %d pages in %d ms (sequential would be ~%d ms), peak in-flight=%d (limit 64)%n",
                found.size(), ms, found.size() * 10, crawler.peakInFlight.get());
        if (!found.equals(expected) || crawler.peakInFlight.get() > 64) throw new AssertionError();
        System.out.println("PASS");
    }
}
