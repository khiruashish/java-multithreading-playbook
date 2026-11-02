# Concurrency Patterns Catalog

<span class="level l3">L3</span> <span class="level l4">L4</span>

| # | Pattern | Problem | Java 8 implementation | Java 21+ implementation |
|---|---|---|---|---|
| 1 | Thread-per-request | Isolate each request | Bounded Tomcat pool | VT per request |
| 2 | Thread pool / worker pool | Reuse expensive threads | `ThreadPoolExecutor` | CPU work only. VTs aren't pooled |
| 3 | Producer–consumer | Decouple rates | `BlockingQueue` | Same, with cheap blocking consumers |
| 4 | Pipeline | Multi-stage processing | Queues between pools | Queues between VT stages |
| 5 | Fan-out / fan-in (scatter-gather) | Parallel calls, combine results | `CompletableFuture.allOf` | `StructuredTaskScope` / VT + `invokeAll` |
| 6 | First-wins / hedging | Tail latency | `anyOf` (no cancel) | `invokeAny`, `ShutdownOnSuccess`, `anySuccessfulResultOrThrow` |
| 7 | Bulkhead | Isolate dependencies | Separate pools | `Semaphore` per dependency |
| 8 | Admission control / load shedding | Prevent overload | Pool + queue size | `Semaphore` at the edge |
| 9 | Circuit breaker | Stop calling a failing dependency | Hystrix (dead) / Resilience4j | Resilience4j |
| 10 | Rate limiter | Respect quotas | Guava `RateLimiter` | Token bucket + `Thread.sleep` (cheap) |
| 11 | Read-copy-update / immutable snapshot | Lock-free reads | `AtomicReference` + copy | Same, with records |
| 12 | Copy-on-write | Rarely-changing lists | `CopyOnWriteArrayList` | Same |
| 13 | Double-checked locking | Lazy init | `volatile` + DCL | Holder idiom / `StableValue` (25 preview) |
| 14 | Guarded suspension | Wait for a condition | `wait`/`notify` | `Lock` + `Condition`, or a queue |
| 15 | Balking | Skip if busy | `AtomicBoolean.compareAndSet` | Same |
| 16 | Monitor object | Encapsulated synchronization | `synchronized` methods | Same (VT-safe on 24+) |
| 17 | Active object / actor | Serialize access per entity | Single-thread executor per entity | VT lane per key (cheap) |
| 18 | Thread-local storage | Per-thread context | `ThreadLocal` | `ScopedValue` |
| 19 | Barrier / phaser | Phased computation | `CyclicBarrier`, `Phaser` | Same |
| 20 | Leader election / lease | One active worker in a cluster | ZooKeeper/DB lock | Same, plus fencing tokens |
| 21 | Single writer | Avoid contention | Disruptor | Disruptor / per-key VT lanes |
| 22 | Async-to-sync bridge | Callback API → blocking API | Rarely worth it | `CompletableFuture.get()` on a VT |

## Balking example

```java
private final AtomicBoolean refreshing = new AtomicBoolean();
public void refreshIfIdle() {
    if (!refreshing.compareAndSet(false, true)) return;     // someone else is refreshing: balk
    try { cache.reload(); } finally { refreshing.set(false); }
}
```

## Active object with a VT lane

```java
final class AccountActor {
    private final BlockingQueue<Runnable> mailbox = new LinkedBlockingQueue<>();
    private long balance;                                    // confined to the actor's VT, no locks
    AccountActor() {
        Thread.ofVirtual().name("account-actor").start(() -> {
            try { while (true) mailbox.take().run(); } catch (InterruptedException e) { /* stop */ }
        });
    }
    CompletableFuture<Long> deposit(long amt) {
        var cf = new CompletableFuture<Long>();
        mailbox.add(() -> cf.complete(balance += amt));
        return cf;
    }
}
```
