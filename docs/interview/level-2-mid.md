# Interview: 3–5 Years

<span class="level l2">L2</span>

## Java Memory Model

??? question "1. Explain happens-before."
    A partial order of actions. If A happens-before B, A's effects are visible to B and A is ordered before it. Edges come from program
    order, monitor unlock→lock, volatile write→read, `start()`, `join()`, j.u.c handoffs, and transitivity.

??? question "2. Why does the JMM exist?"
    Compilers and CPUs reorder and cache memory operations. The JMM defines which results are legal, so code that is correctly synchronized
    behaves as if it were sequentially consistent (the DRF-SC guarantee).

??? question "3. What is safe publication? List idioms."
    Making an object and its state visible to other threads correctly: a static initializer, a `volatile`/`AtomicReference` field, a `final`
    field, a lock-guarded field, or a concurrent collection.

??? question "4. Why must the DCL singleton field be `volatile`?"
    Without it, another thread may see the reference before the constructor's writes (reordering) and get a partially built object.

??? question "5. What guarantees do `final` fields give?"
    If `this` doesn't escape during construction, any thread that sees the reference sees the final fields fully initialized, with no synchronization.

??? question "6. Does `volatile long` matter on 64-bit JVMs?"
    The spec allows tearing of non-volatile `long`/`double` values, and `volatile` forbids it. HotSpot on 64-bit doesn't tear in practice, but use `volatile` to rely on the spec rather than on implementation behaviour.

## Locks and atomics

??? question "7. `ReentrantLock` vs `synchronized`?"
    `ReentrantLock` adds `tryLock`, timeouts, interruptible acquisition, fairness, and multiple `Condition`s, but it needs a `finally` unlock.
    On JDK 21–23, it also avoids VT pinning. On 24+, `synchronized` is fine for VTs too.

??? question "8. What is a fair lock, and what does it cost?"
    A fair lock grants access in FIFO order. That prevents starvation but lowers throughput, because it disables barging.

??? question "9. When would you use `ReadWriteLock`?"
    For read-heavy data with long reads and rare writes. Beware of writer starvation, and note that upgrading from a read lock to a write lock deadlocks.

??? question "10. Explain `StampedLock` optimistic reads."
    `tryOptimisticRead()` returns a stamp without locking. You read the fields, then `validate(stamp)`. If a writer intervened, you fall back to a
    read lock. It isn't reentrant.

??? question "11. What is CAS? What is the ABA problem?"
    Compare-and-swap is an atomic conditional update. ABA: a value changes A→B→A, so a CAS succeeds wrongly. Fix it with stamped references or
    by not reusing nodes.

??? question "12. `AtomicLong` vs `LongAdder`?"
    `LongAdder` stripes updates across cells, so it scales under contention. Its `sum()` isn't an atomic snapshot. Use it for metrics, and use `AtomicLong` when you need an exact value per read, such as IDs.

??? question "13. What is `VarHandle`?"
    The Java 9 API for atomic and ordered access to fields and array elements, with access modes (plain, opaque, acquire/release,
    volatile, CAS). It's the supported replacement for `Unsafe`.

??? question "14. What is a `Condition`?"
    A wait set tied to a `Lock`. Multiple conditions (`notFull`, `notEmpty`) allow targeted `signal()` calls.

??? question "15. What is lock striping?"
    Using several locks for different parts of the data (hash buckets), as the old segmented `ConcurrentHashMap` and `LongAdder` cells do.

## Executors and pools

??? question "16. Explain `ThreadPoolExecutor` parameters and growth behaviour."
    Core size, max size, keep-alive, work queue, thread factory, and rejection handler. The pool fills core threads, then the queue, then grows to max only
    when the queue is full, then rejects.

??? question "17. Why is `Executors.newFixedThreadPool` risky in production?"
    It uses an unbounded `LinkedBlockingQueue`, so it can run out of memory under overload, and `maxPoolSize` is never used.

??? question "18. How do you size a pool for CPU-bound vs IO-bound work?"
    CPU: about the core count. IO: `cores × util × (1 + wait/compute)`. Better still, use Little's Law and measure. With VTs, IO work needs no pool.

??? question "19. Name the rejection policies and when to use each."
    Abort (signal overload to the caller), CallerRuns (natural backpressure), Discard (best-effort), DiscardOldest (only the latest matters).

??? question "20. What is thread-pool-induced deadlock?"
    Tasks in a bounded pool waiting on other tasks queued in the same pool. All threads wait, and the queued tasks never run.

??? question "21. How does a `ScheduledExecutorService` handle exceptions?"
    An exception thrown by a periodic task cancels all later runs silently. Catch everything inside the task.

??? question "22. What is `ForkJoinPool` work stealing?"
    Each worker has a deque. It pushes and pops its own tasks LIFO, while idle workers steal from the other end FIFO. That gives good locality and load balancing.

??? question "23. Problems with parallel streams?"
    They share the common pool, so blocking calls starve the whole JVM. They're inefficient on small or poorly splittable sources,
    and stateful lambdas cause races.

## CompletableFuture

??? question "24. `thenApply` vs `thenCompose`?"
    `thenApply` maps a value (like `map`). `thenCompose` chains a function that returns another `CompletionStage` (like `flatMap`).

??? question "25. Which thread runs a `thenApply` callback?"
    The thread that completes the previous stage, or the caller if the stage is already complete. The `*Async` variants run on the given (or common) executor.

??? question "26. How do you handle errors in a CF chain?"
    `exceptionally` (recover), `handle` (value or error), `whenComplete` (side effect), and `exceptionallyCompose` (Java 12).

??? question "27. How do you add timeouts to a CF?"
    `orTimeout` (fails with `TimeoutException`) or `completeOnTimeout(default)`, both added in Java 9. Neither cancels the underlying work.

??? question "28. Does `cancel(true)` interrupt a CF's task?"
    No. `CompletableFuture` isn't connected to the thread running it, so the flag is ignored.

??? question "29. `allOf` vs `anyOf`?"
    `allOf` completes when all complete (and returns `Void`). `anyOf` completes with the first completion, even an exceptional one.

## ThreadLocal and context

??? question "30. What are the risks of `ThreadLocal` in thread pools?"
    Values leak into the next task on the same pooled thread, and you get memory leaks (especially classloader leaks in app servers). Always `remove()`.

??? question "31. How does MDC work with async code?"
    It's `ThreadLocal`-based, so you copy the context map into tasks with a `TaskDecorator` or context-propagation wrappers.

## Virtual threads (mid-level)

??? question "32. How do virtual threads differ internally from platform threads?"
    A VT is a continuation scheduled on a `ForkJoinPool` of carriers, and its stack lives on the heap. A platform thread maps 1:1 to an OS thread.

??? question "33. What is pinning?"
    A VT that can't unmount while blocked, so it holds its carrier. On JDK 21–23 this happens inside `synchronized` and native frames. On 24+, only in native code and class initializers.

??? question "34. How do you detect pinning?"
    The JFR event `jdk.VirtualThreadPinned`. On 21–23 you can also use `-Djdk.tracePinnedThreads=full`.

??? question "35. Once threads are virtual, how do you limit concurrency to a database?"
    Through the connection pool size (HikariCP) plus `connectionTimeout`, and with a `Semaphore` for other resources.

??? question "36. What does `spring.threads.virtual.enabled=true` change?"
    Tomcat/Jetty request handling, the `@Async` executor, `@Scheduled`, and messaging listeners all run on virtual threads.

??? question "37. Are `ThreadLocal`s supported on VTs?"
    Yes, but don't use them as caches (no reuse, one copy per VT). For context, prefer `ScopedValue`.

??? question "38. What happens with `@Transactional` when you fork work to another VT?"
    The forked work isn't in the transaction, because the connection and transaction are bound to the original thread.

??? question "39. Why do VTs need explicit backpressure?"
    The thread pool used to cap concurrency implicitly. With unlimited VTs, downstreams can be flooded unless you add semaphores or rate limits.

??? question "40. What's the risk of CPU-heavy work on VTs?"
    VTs aren't time-sliced. CPU-bound VTs occupy the carriers and starve the IO-bound ones. Use a bounded platform pool for CPU work.

## Collections and synchronizers

??? question "41. Why does `ConcurrentHashMap` disallow null keys and values?"
    `get()` returning `null` would be ambiguous (absent vs a null value), and you can't resolve that atomically with `containsKey` in concurrent code.

??? question "42. Is `ConcurrentHashMap.size()` exact?"
    It's an estimate during concurrent updates (summed counter cells). Use `mappingCount()` for a `long`.

??? question "43. `CyclicBarrier` vs `CountDownLatch`?"
    A barrier is reusable, and N parties wait for each other. A latch is one-shot, and waiters wait for N events from anyone.

??? question "44. What is a `Semaphore` used for?"
    Limiting concurrent access to N permits, as in bulkheads, connection caps, and rate limiting.

??? question "45. What is `Phaser`?"
    A reusable barrier with dynamic registration and multiple phases. Parties can join and leave.
