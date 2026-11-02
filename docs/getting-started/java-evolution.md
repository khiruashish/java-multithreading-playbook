# Java 8 → 25: Concurrency Feature Evolution

<span class="level l2">L2</span> <span class="level l3">L3</span>

This is the complete list of concurrency-relevant changes between the Java 8 baseline and today. **Bold** marks
the ones that change how you design code; the rest are tuning or convenience.

## Release-by-release

| Release | Type | Concurrency feature | Why it matters |
|---|---|---|---|
| **8** (2014) | LTS | **`CompletableFuture` / `CompletionStage`** | Composable async pipelines with no callback hell |
| 8 | | **Parallel streams** (on the common `ForkJoinPool`) | Data parallelism in one call; easy to misuse |
| 8 | | **`StampedLock`** | Optimistic reads, faster than `ReentrantReadWriteLock` for read-mostly data |
| 8 | | **`LongAdder`, `LongAccumulator`, `DoubleAdder`** | Striped counters with no CAS contention under heavy writes |
| 8 | | `ConcurrentHashMap` rewrite (no segments, tree bins), `computeIfAbsent`, `forEach/reduce/search` bulk ops | Atomic compound ops on maps |
| 8 | | `ForkJoinPool.commonPool()` | Shared default pool, used by parallel streams and `*Async` methods |
| 8 | | `@sun.misc.Contended` (internal) | Prevents false sharing in JDK classes |
| 9 | Feature | **`java.util.concurrent.Flow`** (Reactive Streams interfaces), `SubmissionPublisher` | Standard backpressure contract |
| 9 | | **`VarHandle`** (JEP 193) | Supported replacement for `sun.misc.Unsafe` CAS and fences |
| 9 | | `CompletableFuture.orTimeout`, `completeOnTimeout`, `delayedExecutor`, `copy`, `failedFuture` | Timeouts without extra schedulers |
| 9 | | `Thread.onSpinWait()` | CPU hint for spin loops |
| 10 | Feature | Thread-local handshakes (JEP 312) | Stops individual threads without a global safepoint |
| 11 | LTS | `HttpClient` (async, `CompletableFuture`-based) | Standard non-blocking HTTP |
| 14 | Feature | Helpful NPEs, records preview | Indirect: immutable data carriers are good for concurrency |
| 15 | Feature | Biased locking disabled and deprecated (JEP 374); removed in 18 | Uncontended `synchronized` costs slightly more, contended costs less |
| 16 | Feature | Records final, `Stream.toList()` | Immutable DTOs shared across threads safely |
| 17 | LTS | Sealed classes; strong encapsulation of JDK internals | `Unsafe` hacks discouraged |
| 19 | Feature | Virtual threads **preview** (JEP 425); `ExecutorService` is now `AutoCloseable`; `Future.resultNow/exceptionNow/state()` | try-with-resources executors |
| 19 | | Structured concurrency **incubator** (JEP 428) | |
| 20 | Feature | VT 2nd preview (JEP 436); Scoped values incubator (JEP 429) | |
| **21** (2023) | **LTS** | **Virtual threads FINAL (JEP 444)**; `Thread.ofVirtual()`, `Thread.Builder`, `Executors.newVirtualThreadPerTaskExecutor()` | Thread-per-request scales to millions |
| 21 | | Structured concurrency **preview** (JEP 453), Scoped values **preview** (JEP 446) | `--enable-preview` |
| 21 | | Generational ZGC (JEP 439) | Lower GC overhead with millions of short-lived VT stacks |
| 22 | Feature | SC 2nd preview (JEP 462), SV 2nd preview (JEP 464) | |
| 23 | Feature | SC 3rd preview (JEP 480), SV 3rd preview (JEP 481); ZGC generational by default | |
| **24** (2025) | Feature | **Synchronize virtual threads without pinning (JEP 491)** | `synchronized` blocks/methods unmount; `jdk.tracePinnedThreads` removed |
| 24 | | SC 4th preview (JEP 499), SV 4th preview (JEP 487) | |
| **25** (2025) | **LTS** | **Scoped values FINAL (JEP 506)** | Production-safe `ThreadLocal` replacement |
| 25 | | SC 5th preview (JEP 505): new `StructuredTaskScope.open(Joiner)` API | API reshaped; still preview |
| 25 | | Stable values preview (JEP 502) | Thread-safe lazy initialization without double-checked locking |
| 26 | Feature | SC 6th preview (JEP 525) | Still evolving |

!!! info "Which version should I target?"
    * **Java 21 LTS** is the minimum for virtual threads in production. Audit `synchronized` around blocking IO,
      because it pins carriers.
    * **Java 24** is the first release where `synchronized` is VT-friendly. It is a non-LTS release, so most enterprises
      move from **21 → 25 LTS** and get JEP 491 plus final scoped values.
    * Treat **structured concurrency** as preview in every release up to and including 26. Wrap it behind your own small
      abstraction if you adopt it (see [Structured Concurrency](../modern/structured-concurrency.md)).

## Old way vs new way: the API map

| Need | Java 8 way | Java 21/24/25 way |
|---|---|---|
| Run one task per request | Fixed `ThreadPoolExecutor(200)` | `Executors.newVirtualThreadPerTaskExecutor()` |
| Start a thread | `new Thread(r).start()` | `Thread.ofVirtual().name("worker-", 0).start(r)` or `Thread.startVirtualThread(r)` |
| Shut down an executor | `shutdown()` + `awaitTermination()` in a `finally` block | `try (var ex = ...) { }` (`AutoCloseable` since 19) |
| Fan-out to N services | `CompletableFuture.allOf(...)` + `thenCombine` chains | Plain blocking calls inside `StructuredTaskScope` (preview), or VT executor + `invokeAll` |
| Timeout an async call | `ScheduledExecutorService` + `cancel` | `cf.orTimeout(2, SECONDS)` (9+) or `scope.joinUntil` (21–24) / `Joiner` + config timeout (25) |
| Per-request context | `ThreadLocal` + `try/finally remove()` | `ScopedValue.where(KEY, v).run(...)` (final in 25) |
| Limit concurrency to a downstream | Size of thread pool | `Semaphore` (threads are no longer the scarce resource) |
| Lazy singleton | Double-checked locking with `volatile` | Holder idiom, or `StableValue` (25 preview) |
| Read-mostly cache lock | `ReentrantReadWriteLock` | `StampedLock` optimistic read, or `ConcurrentHashMap` |
| Hot counter | `AtomicLong` | `LongAdder` |
| Low-level CAS | `sun.misc.Unsafe` | `VarHandle` |
| Pinning-safe locking around IO | `ReentrantLock` (21–23) | `synchronized` is fine again (24+) |
| Get a result from a done `Future` | `try { f.get() } catch (...)` | `f.resultNow()` / `f.state()` (19+) |
