# Interview: 6–9 Years

<span class="level l3">L3</span>

## Internals

??? question "1. How does HotSpot implement `synchronized` today?"
    Lightweight locking (CAS on the header, or the newer lightweight mode in 21+) when uncontended. It inflates to an `ObjectMonitor` under contention or
    `wait()`. Biased locking was removed in JDK 18.

??? question "2. What is a memory barrier, and which ones does `volatile` emit on x86?"
    A barrier is an ordering constraint. x86 is TSO, so only store→load reordering needs a barrier: a volatile write emits a `lock`-prefixed instruction
    (or `mfence`), and volatile reads are plain loads.

??? question "3. Explain store buffering and why `0,0` is possible in Dekker's test."
    Each core's write sits in its store buffer while it reads the other variable from cache, so both reads see 0. Making the fields `volatile` forbids that outcome.

??? question "4. What is false sharing, and how do you fix it?"
    Independent variables on one cache line cause invalidation ping-pong. Fix it with padding, `@Contended`, or JDK classes like `LongAdder`.

??? question "5. How does `ConcurrentHashMap` (Java 8+) work?"
    An array of bins. Empty bins are filled with a CAS, and non-empty bins are locked by synchronizing on the head node. Long bins turn into red-black trees (TreeBins).
    Resizing is cooperative (threads help transfer), the counter is LongAdder-style, and reads are lock-free with volatile semantics.

??? question "6. How does `AbstractQueuedSynchronizer` work?"
    A volatile `int state` plus a CLH-style FIFO queue of waiting nodes. Acquire tries a CAS on state, and on failure it enqueues and
    `LockSupport.park()`s. Release updates state and unparks the successor. `ReentrantLock`, `Semaphore`, and `CountDownLatch` are built on it.

??? question "7. What is `LockSupport.park/unpark`?"
    A per-thread permit primitive. `unpark` before `park` makes the `park` return immediately. VTs implement `park` by unmounting.

## Virtual threads in depth

??? question "8. Describe mount and unmount."
    When it's scheduled, the VT's continuation runs on a carrier (frames are thawed from heap stack chunks). When it blocks, `Continuation.yield` freezes the changed
    frames to the heap, and the carrier is released. The VT may resume on another carrier.

??? question "9. How does a blocking socket read avoid blocking the carrier?"
    The JDK's socket implementation uses non-blocking IO. On `EAGAIN` it registers the fd with a poller (epoll/kqueue/IOCP) and parks the VT.
    The poller unparks the VT when the fd is ready.

??? question "10. Which operations capture the carrier instead of unmounting?"
    File IO and DNS lookups (the scheduler compensates with extra carriers), native code or JNI, and class initialization. On 21–23 also `synchronized`
    blocking and `Object.wait()`.

??? question "11. What exactly did JEP 491 change?"
    Monitors are now associated with the virtual thread, not the carrier. VTs can unmount while holding, contending for, or `wait()`ing on monitors.
    `jdk.tracePinnedThreads` was removed.

??? question "12. What are the scheduler tuning knobs, and when would you touch them?"
    `jdk.virtualThreadScheduler.parallelism` and `maxPoolSize` (plus `minRunnable`). Touch them rarely, for example when container CPU detection is off or
    when compensation hits the cap.

??? question "13. Are virtual threads preemptive?"
    No time-slicing. A VT runs until it blocks, yields, or finishes. CPU-bound loops monopolize their carriers.

??? question "14. How do you get a thread dump that includes VTs?"
    `jcmd <pid> Thread.dump_to_file -format=json file`. `jstack` shows only platform threads (the carriers).

??? question "15. Why is `ThreadLocal` caching harmful with VTs, while context is acceptable?"
    Caching relies on thread reuse, which VTs don't have, so you get one copy per task and millions of objects. Context is per request anyway, so it costs only the value.

## Modern APIs

??? question "16. What problem does structured concurrency solve?"
    Unstructured `Future`s leak tasks on failure, don't propagate cancellation, and lose the parent–child relation. SC bounds subtask lifetimes to a
    scope, cancels siblings, propagates errors, and makes task trees observable.

??? question "17. Compare `ShutdownOnFailure` and `ShutdownOnSuccess` (21–24)."
    The first shuts the scope down on the first failure (all results required). The second shuts it down on the first success (any result is enough).

??? question "18. How did the SC API change in Java 25?"
    `StructuredTaskScope.open(Joiner, config)` replaced the subclasses: `Joiner.awaitAllSuccessfulOrThrow()`, `anySuccessfulResultOrThrow()`,
    `allSuccessfulOrThrow()`, and custom `Joiner`s. Timeouts are set in config, and `join()` throws `FailedException`.

??? question "19. Why are scoped values better than `ThreadLocal`?"
    They're immutable per binding, bounded in lifetime, inherited cheaply by SC subtasks, and JIT-friendly. They're final in Java 25.

??? question "20. Do executor tasks inherit scoped values?"
    No. Only `StructuredTaskScope.fork` does. For other executors, capture the value and rebind it explicitly.

## Performance and debugging

??? question "21. A service's p99 latency doubled after enabling VTs on JDK 21. How do you investigate?"
    Check `jdk.VirtualThreadPinned` in JFR. Look at JSON thread dumps for VTs piling up in one frame (DB pool, `synchronized`). Check CPU-bound work on VTs,
    downstream saturation from the extra concurrency, and GC.

??? question "22. Throughput didn't improve after enabling VTs. Why?"
    The service is CPU-bound, or limited by the DB or connection pools, a bottleneck in a lock or rate limiter, or a downstream. VTs only remove the
    thread bottleneck.

??? question "23. How do you find lock contention in production?"
    JFR `JavaMonitorEnter`/`ThreadPark` events, async-profiler `-e lock`, or several thread dumps showing many threads BLOCKED on one address.

??? question "24. How do you benchmark concurrent code correctly?"
    JMH with forks, warm-up, `@Param` concurrency levels, blackholes or returned values, and `-prof gc`. Then load-test the service at a
    constant rate (wrk2/k6) to avoid coordinated omission.

??? question "25. What is coordinated omission?"
    A closed-loop load generator waits for slow responses before sending more, which hides queueing delay. Use constant-rate tools.

??? question "26. How do you test memory-model correctness?"
    jcstress: it runs the actors many times across interleavings and classifies the outcomes.

## Design and trade-offs

??? question "27. Design a bounded producer/consumer pipeline with backpressure, without reactive libraries."
    Stages connected by bounded `BlockingQueue`s, with N VT workers per stage and poison pills for shutdown. A full queue blocks producers (backpressure).
    Add metrics on queue depth.

??? question "28. How do you implement a bulkhead with VTs?"
    A semaphore per dependency with `tryAcquire(timeout)`. Fail fast with 503 and track usage and rejections. Avoid thread-pool bulkheads.

??? question "29. How do you propagate deadlines across a fan-out?"
    Compute a deadline at the edge. Each call uses the remaining time as its timeout, the fan-out scope enforces the overall deadline, and you propagate it via a header.

??? question "30. When is reactive still the better choice than VTs?"
    Streaming with end-to-end backpressure, very high fan-out with tiny CPU per request where the last few percent of efficiency matter, and existing healthy reactive stacks.

??? question "31. How do you keep per-key ordering while processing in parallel?"
    Per-key lanes, where a single consumer per key drains a queue, using CHM `compute` for atomic lane lifecycle, plus contiguous offset commits.

??? question "32. Optimistic vs pessimistic locking in a DB-backed service?"
    Optimistic (`@Version`) for low-contention updates, retrying on conflict. Pessimistic (`FOR UPDATE`) for hot rows, with tiny transactions, or
    redesign with atomic SQL updates.

??? question "33. How would you implement a rate limiter?"
    A token bucket or sliding window. In-process: a lock-protected bucket that sleeps outside the lock (cheap on VTs). Distributed: Redis Lua script
    or a gateway.

??? question "34. What's wrong with `synchronized (this) { httpClient.send(...) }`?"
    The lock is held across IO, which serializes all callers. It pins VTs on 21–23, and the lock is public. Narrow the critical section to state changes only.

??? question "35. How do you make a cache refresh non-blocking for readers?"
    Use an immutable snapshot in an `AtomicReference`. One thread refreshes it (balking with `compareAndSet` on a flag) while readers keep reading the old snapshot.

## Scenario questions

??? question "36. Your Kafka consumer lags. Would VTs help?"
    Only if processing is IO-bound **and** you parallelize within a partition (per-key lanes). VTs alone don't add parallelism beyond the number of consumers, which
    is capped by partitions.

??? question "37. 10k scheduled reminders: timers or VTs?"
    Sleeping VTs are fine for short horizons within one process lifetime. For durability, use a persistent scheduler (Quartz, DB polling with `SKIP LOCKED`).

??? question "38. How do you shut down a service gracefully with in-flight VTs?"
    Graceful server shutdown, a readiness probe that goes down, closing executors (`shutdown` + `awaitTermination` + `shutdownNow`), and an aligned K8s grace period.

??? question "39. How do you avoid connection storms after enabling VTs?"
    Small fixed DB pools, PgBouncer, admission control, and per-dependency semaphores. Raise limits only after load tests.

??? question "40. A `parallelStream()` in a request handler makes the whole JVM slow. Why?"
    It uses the common FJ pool shared by every request (and by `CompletableFuture` async defaults). Concurrent requests contend for it, and blocking starves it.

??? question "41. How do you propagate tracing context into tasks you fork yourself?"
    OpenTelemetry `Context.taskWrapping(executor)` or Micrometer `ContextExecutorService.wrap`. Structured scopes plus scoped values in the future.

??? question "42. Explain the difference between linearizability and sequential consistency."
    Both give a single total order. Linearizability also respects real-time order: an operation takes effect between its invocation and its response.
    `Atomic*` operations are linearizable.

??? question "43. What is the single-writer principle?"
    Only one thread mutates a given piece of state, and others send it messages. This avoids contention and locks (Disruptor, actors, partitions).

??? question "44. `invokeAny` vs `anyOf`?"
    `invokeAny` returns the first **successful** result and cancels the others. `anyOf` completes with the first completion, success or failure, and cancels nothing.

??? question "45. How do VTs change the way you write retries and timeouts?"
    Write them as plain loops with `Thread.sleep` and blocking calls with timeouts. There's no need for schedulers and CF chains, and deadline budgets stay explicit.
