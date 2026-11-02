# Synchronizers

<span class="level l2">L2</span>

| Synchronizer | Mental model | Reusable | Typical use |
|---|---|---|---|
| `CountDownLatch` | A gate that opens after N events | No | Wait for N services to start, or N tasks to finish |
| `CyclicBarrier` | N threads meet, then all proceed | Yes | Iterative simulations, phased computations |
| `Semaphore` | N permits | Yes | **Limit concurrency** (bulkheads, connection caps). Essential with VTs |
| `Phaser` (7) | Dynamic barrier with registration | Yes | Variable parties, multi-phase work |
| `Exchanger` | Two threads swap objects | Yes | Double-buffering pipelines |

## CountDownLatch

```java
CountDownLatch ready = new CountDownLatch(3);
for (String svc : List.of("db", "cache", "broker")) {
    Thread.startVirtualThread(() -> { connect(svc); ready.countDown(); });
}
if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("startup timed out");
```

## CyclicBarrier

```java
CyclicBarrier barrier = new CyclicBarrier(4, () -> log.info("phase done"));  // barrier action
for (int w = 0; w < 4; w++) {
    int id = w;
    Thread.ofPlatform().start(() -> {
        for (int step = 0; step < 10; step++) {
            simulate(id, step);
            try { barrier.await(); } catch (Exception e) { return; }      // BrokenBarrierException if a party fails
        }
    });
}
```

## Semaphore: the virtual-thread-era rate limiter

```java
public final class Bulkhead {
    private final Semaphore permits;
    private final Duration maxWait;
    public Bulkhead(int maxConcurrent, Duration maxWait) {
        this.permits = new Semaphore(maxConcurrent, true);
        this.maxWait = maxWait;
    }
    public <T> T call(Callable<T> c) throws Exception {
        if (!permits.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS))
            throw new BulkheadFullException();                 // shed load instead of queuing forever
        try { return c.call(); } finally { permits.release(); }
    }
}
```

## Phaser

```java
Phaser phaser = new Phaser(1);                        // register self
for (Task t : tasks) {
    phaser.register();
    Thread.startVirtualThread(() -> {
        t.stage1(); phaser.arriveAndAwaitAdvance();
        t.stage2(); phaser.arriveAndDeregister();
    });
}
phaser.arriveAndAwaitAdvance();                      // wait for stage 1 of all tasks
phaser.arriveAndDeregister();
```

## Old vs new: wait for N tasks

=== "Java 8"

    ```java
    ExecutorService ex = Executors.newFixedThreadPool(10);
    CountDownLatch done = new CountDownLatch(urls.size());
    ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();
    for (String u : urls) ex.execute(() -> { try { results.add(fetch(u)); } finally { done.countDown(); } });
    done.await();
    ex.shutdown();
    ```

=== "Java 21"

    ```java
    List<String> results;
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
        results = ex.invokeAll(urls.stream().<Callable<String>>map(u -> () -> fetch(u)).toList())
                    .stream().map(Future::resultNow).toList();   // throws if any task failed
    }
    ```
