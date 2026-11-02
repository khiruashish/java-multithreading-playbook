# L7: Pitfalls & Anti-patterns

<span class="level l2">L2</span> → <span class="level l4">L4</span>

## 1. Pooling virtual threads
```java
Executors.newFixedThreadPool(100, Thread.ofVirtual().factory());   // ❌
```
A pool exists to reuse expensive threads, and VTs are not expensive. Pooling brings back queueing and loses the benefits.
**Fix:** `newVirtualThreadPerTaskExecutor()` + `Semaphore(100)`.

## 2. Pinning on JDK 21–23
Blocking IO inside `synchronized`. **Fix:** `ReentrantLock`, or upgrade to JDK 24+. See [L3](03-pinning.md).

## 3. CPU-bound work on VTs
Video encoding on VTs monopolizes the carriers, because there's no preemption. **Fix:** send CPU work to a fixed platform pool sized to the core count.

## 4. Unbounded fan-out melts downstreams
100k VTs → 100k simultaneous HTTP calls → the downstream falls over, or you run out of ephemeral ports / file descriptors.
**Fix:** bulkheads and admission control ([L5](05-patterns.md)).

## 5. ThreadLocal caches
`ThreadLocal<ObjectMapper>` with 1M VTs means 1M ObjectMappers. **Fix:** `ObjectMapper` is thread-safe after
configuration, so share one instance.

## 6. Huge `ThreadLocal`/MDC payloads
Even context-only ThreadLocals get multiplied by the number of threads. Keep MDC tiny, or move to `ScopedValue`.

## 7. Expecting lower latency
VTs improve **throughput under concurrency**. A single request's latency is unchanged, or slightly higher (around 1–2%).

## 8. Forgetting timeouts
A VT blocked forever is a leak: it holds heap, sockets, and semaphore permits. Every IO call needs a timeout
(HTTP connect/read timeouts, JDBC `queryTimeout`, Hikari `connectionTimeout`).

## 9. Assuming `Thread.currentThread()` identity is stable over the carrier
Native code and libraries that key data by OS thread id (some profilers and old APM agents) may misbehave. Upgrade your
APM agents.

## 10. Busy-wait spin loops
`while (!flag) {}` on a VT never yields its carrier. Use `LockSupport.park`/`unpark`, `CountDownLatch`, or a queue.

## 11. `Thread.yield()` as a scheduling tool
It's a hint at best. Don't design around it.

## 12. Calling `join()` on an executor's tasks inside `synchronized` (JDK 21–23)
`synchronized (x) { future.get(); }` pins the carrier for the whole wait.

## 13. Daemon surprise
VTs are daemon threads. A `main` that starts VTs and returns exits the JVM immediately, and in Spring, `@Scheduled`
alone won't keep the JVM alive. Set `spring.main.keep-alive=true`.

## 14. Using `parallelStream()` from a VT for IO
The work still runs on the common FJ pool (platform threads). Use the VT executor instead.

## 15. Over-subscribing a native resource
File IO and DNS capture carriers. Reading 50k files at once spawns compensating carriers up to the 256 cap, and the
remaining reads queue behind them. **Fix:** a `Semaphore` sized to the disk's concurrency.

## 16. Leaking executors
`Executors.newVirtualThreadPerTaskExecutor()` assigned to a field but never closed still works (the threads are daemons),
but you lose structured shutdown. Close it in `@PreDestroy`, or create it per scope with try-with-resources.

## 17. Mixing reactive and VT in one request
Calling `.block()` inside a WebFlux handler is still wrong (it blocks an event-loop thread), even if the inner work
uses VTs. Pick one model per request path.
