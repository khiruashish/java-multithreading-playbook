# Virtual Threads Deep-Dive Q&A (50 questions)

From basic to architect, all about virtual threads.

## Basics (L1)

??? question "1. In one sentence, what is a virtual thread?"
    A `java.lang.Thread` that the JVM, not the OS, schedules onto a small pool of carrier threads, so blocking it is cheap.

??? question "2. Which JEP made virtual threads final, and in which release?"
    JEP 444, in Java 21 (LTS). They were previewed in 19 (JEP 425) and 20 (JEP 436).

??? question "3. Why does `main` exit immediately after `Thread.startVirtualThread(...)`?"
    Virtual threads are daemon threads. Call `join()` on the thread, or use an executor's `close()`.

??? question "4. Will a CPU-bound loop run faster on 1,000 VTs than on 8 platform threads (8 cores)?"
    No. Only 8 carriers can run at once, and the extra VTs add scheduling overhead.

??? question "5. Why is `newFixedThreadPool(100, Thread.ofVirtual().factory())` an anti-pattern?"
    It pools cheap threads and brings back queueing. Use a VT-per-task executor plus a `Semaphore(100)` to limit concurrency.

??? question "6. Do existing libraries need changes to run on VTs?"
    Mostly no. Blocking JDK APIs were retrofitted. Libraries that held `synchronized` around IO pinned on 21–23 (fixed in 24), and native/JNI code still pins.

??? question "7. Can you set priority or daemon status on a VT?"
    No. Daemon is always true, and priority is fixed at `NORM_PRIORITY` (`setPriority` has no effect).

??? question "8. What does `Thread.currentThread()` return inside a VT?"
    The virtual thread itself, never the carrier.

??? question "9. How many VTs can you create?"
    Millions. The limit is heap memory for their stacks and the objects they reference.

??? question "10. Does a VT have a thread ID and a name?"
    Yes. It has an ID, and an optional name (empty by default). Name them for debugging.

## Mechanics (L2)

??? question "11. What is a carrier thread?"
    A platform thread in the VT scheduler's `ForkJoinPool` that runs (mounts) virtual threads.

??? question "12. What is a continuation?"
    A resumable sequence of frames. `yield` saves its stack to the heap, and `run` restores it. It's the internal building block of VTs.

??? question "13. What is the default scheduler, and how big is it?"
    A FIFO-mode `ForkJoinPool` with parallelism equal to available processors, growing up to 256 threads to compensate for captured carriers.

??? question "14. Where does a parked VT's stack live?"
    In heap `StackChunk` objects, which hold only the frames in use.

??? question "15. What happens when a VT calls `Thread.sleep`?"
    It schedules a timed unpark and yields. The carrier is freed for other VTs.

??? question "16. What happens when a VT reads from a socket with no data?"
    The JDK registers the socket with the poller, parks the VT, and unmounts it. When data arrives the poller unparks it, and it resumes on some carrier.

??? question "17. What happens when a VT does file IO?"
    File IO can't use the poller on most OSes, so the VT captures the carrier and the scheduler temporarily adds a compensating carrier.

??? question "18. What's the cost of creating a VT?"
    Around a microsecond: an object allocation with no OS call. Creating a platform thread costs tens to hundreds of microseconds plus memory reservation.

??? question "19. Can a VT resume on a different carrier?"
    Yes. Never rely on carrier identity or carrier-local state.

??? question "20. Are VTs time-sliced?"
    No. A VT runs until it blocks, yields, or terminates.

## Pinning and compatibility (L3)

??? question "21. Define pinning."
    A VT that can't unmount when it blocks, so it holds the carrier OS thread for the whole wait.

??? question "22. What pinned on JDK 21–23?"
    Blocking inside `synchronized` (including contended entry), `Object.wait()`, native/JNI frames, and class initializers.

??? question "23. What still pins on JDK 24+?"
    Native frames (JNI, or foreign upcalls on the stack) and class initializers that block.

??? question "24. How do you find pinning?"
    JFR `jdk.VirtualThreadPinned` (20 ms threshold by default). On 21–23 you can also use `-Djdk.tracePinnedThreads=full`.

??? question "25. On JDK 21, you find `synchronized` around a JDBC call. What are your options?"
    Replace it with `ReentrantLock`, move the IO outside the lock, upgrade the library, or upgrade to JDK 24+.

??? question "26. Should you replace every `synchronized` with `ReentrantLock`?"
    No. Short, CPU-only critical sections never block, so their pinning is harmless. Fix only the hot paths that block, based on JFR evidence. On 24+, keep `synchronized`.

??? question "27. Do `ThreadLocal`s work on VTs?"
    Yes, including inheritable ones. Avoid using them as caches. Use `ScopedValue` for context (final in 25).

??? question "28. Are VTs visible in `ThreadMXBean` and `jstack`?"
    No, those show platform threads only. Use `jcmd Thread.dump_to_file` (and JSON format) for VTs.

??? question "29. What's the JDK 24 MXBean for the scheduler?"
    `jdk.management.VirtualThreadSchedulerMXBean`: parallelism, pool size, mounted count, queued count.

??? question "30. Can a VT be interrupted while blocked in IO?"
    Yes. Interruptible socket operations abort with an exception (the channel is closed for NIO channels), so cancellation works.

## Production (L3–L4)

??? question "31. What becomes the concurrency limit once threads are virtual?"
    Resources: CPU, DB connections, downstream capacity, memory, file descriptors. Limit them explicitly.

??? question "32. How do you implement load shedding with VTs?"
    An admission semaphore at the edge with `tryAcquire`, returning 503 or 429 with `Retry-After` when it's full.

??? question "33. How do you size the Hikari pool when VTs are on?"
    The same way as before: λ × DB time with headroom, kept small and checked against the DB's limits. Don't enlarge it just because there are more threads.

??? question "34. What's the effect of VTs on downstream load?"
    It usually rises at the same incoming traffic, because the service no longer queues internally. Coordinate with downstream owners and add bulkheads.

??? question "35. How do you do CPU-heavy work in a VT-based service?"
    Submit it to a bounded platform executor (size ≈ cores) and block the VT on its result.

??? question "36. How do you ensure context (MDC, security, tracing) flows into VTs you create?"
    Wrap executors (`DelegatingSecurityContextExecutorService`, OTel `Context.taskWrapping`, Micrometer `ContextExecutorService`), or use
    `ScopedValue` with structured concurrency.

??? question "37. Does `@Async` with VTs need a queue capacity?"
    No queue. Use `spring.task.execution.simple.concurrency-limit` to bound it if needed.

??? question "38. What's the impact on GC?"
    More, smaller allocations (stack chunks). Generational ZGC or G1 handles it well. Watch the allocation rate and heap after GC.

??? question "39. How do you measure the benefit before rolling out?"
    Load-test at production-like concurrency with VTs on and off (configuration toggle), comparing max sustainable RPS at the SLO, p99, CPU, memory, and pool waiters.

??? question "40. What is the main architectural risk of VTs?"
    Removing the accidental bulkhead (pool size) without adding explicit limits. Overload then propagates to downstreams and memory.

## Architect (L4)

??? question "41. VT vs reactive: give three cases for each."
    VT: CRUD/aggregator microservices, JDBC/JPA-heavy services, teams without reactive skills. Reactive: SSE/WebSocket streaming at scale,
    end-to-end backpressured pipelines, existing healthy reactive codebases or gateways.

??? question "42. How do VTs affect your microservice timeout strategy?"
    They make it more important. Blocking forever is cheap for the thread but costly for resources. Use deadline propagation, per-call timeouts, and scope
    deadlines.

??? question "43. How would you roll VTs out across 100 services?"
    Tier by IO-heaviness. Ship the platform template with guardrails, toggle by configuration, run canaries with pinning and latency dashboards, track a fleet scorecard, and keep a rollback plan.

??? question "44. Could a VT-based service run out of memory where a platform-thread service wouldn't?"
    Yes. Unbounded in-flight work (no admission control, no timeouts) accumulates parked VTs and their request state, where a pool would have
    rejected or queued it at the edge.

??? question "45. How do structured concurrency and VTs complement each other?"
    VTs make forking cheap. SC makes forking safe: bounded lifetimes, cancellation, error propagation, observability, and scoped value inheritance.

??? question "46. What does a JSON thread dump give you that jstack doesn't?"
    Every VT, grouped by thread container (executor or scope), showing the parent–child structure and making mass analysis (`jq` grouping) easy.

??? question "47. How do VTs change the way you size Kubernetes pods?"
    Size by CPU and memory demand, not thread count. Make sure `cpu` limits give at least 2 carriers, and watch CFS throttling.

??? question "48. When would you set `jdk.virtualThreadScheduler.parallelism` manually?"
    When container CPU detection is wrong, or to deliberately limit carrier parallelism (for isolation or benchmarks). It's rare.

??? question "49. What did Netflix-style 'VT deadlock' incidents on JDK 21 teach the industry?"
    With few carriers, pinned VTs waiting on a lock held by an unmounted VT can starve all carriers. The lessons were to audit `synchronized`+blocking on 21–23,
    upgrade libraries, and move to JDK 24+ (JEP 491).

??? question "50. What would you still not do with VTs today?"
    Pool them, run long CPU-bound work on them, rely on them for fairness or priority, keep large `ThreadLocal` caches, or skip explicit limits and timeouts.
