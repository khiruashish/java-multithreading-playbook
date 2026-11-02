# Concurrency Libraries

<span class="level l2">L2</span> → <span class="level l4">L4</span>

Before hand-writing a cache, rate limiter, or lock-free queue, check this list. These libraries are battle-tested and
well-benchmarked. (Coordinates are indicative. Check Maven Central for current versions.)

## Caching

| Library | Use | Concurrency highlights |
|---|---|---|
| **Caffeine** (`com.github.ben-manes.caffeine:caffeine`) | In-process cache | W-TinyLFU eviction, lock-amortized reads, `AsyncLoadingCache` = single-flight loading, `refreshAfterWrite` |
| Guava Cache | Legacy cache | Superseded by Caffeine |

```java
AsyncLoadingCache<String, Product> cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .refreshAfterWrite(Duration.ofMinutes(5))     // serve stale while ONE thread refreshes
        .buildAsync(id -> productClient.get(id));     // concurrent misses for one key → one load
```

## Resilience: bulkheads, rate limits, retries

| Library | Use | Notes |
|---|---|---|
| **Resilience4j** | CircuitBreaker, Bulkhead (semaphore), RateLimiter, Retry, TimeLimiter | Prefer the **semaphore** bulkhead with virtual threads |
| **Failsafe** | Retry, circuit breaker, timeout, bulkhead, fallback | Small, composable policies |
| **Bucket4j** | Token-bucket rate limiting, local or distributed (Redis/Hazelcast/JCache) | Production version of [problem 17](../coding-interview/17-rate-limiter.md) |

## High-performance queues and data structures

| Library | Use |
|---|---|
| **JCTools** | Lock-free SPSC/MPSC/MPMC queues (used by Netty and others), faster than j.u.c queues for specific producer/consumer shapes |
| **LMAX Disruptor** | Ring-buffer, single-writer event processing with very low latency (Log4j 2 async loggers use it) |
| **Agrona** | Low-level buffers, ring buffers, and agents (used by Aeron) |
| **Eclipse Collections**, fastutil | Primitive collections. Fewer objects means less GC pressure under load |

## Asynchronous and reactive programming

| Library | Model |
|---|---|
| Project Reactor | `Mono`/`Flux`, backpressure (Spring WebFlux) |
| RxJava 3 | Observable/Flowable |
| Vert.x | Event loop + verticles (also supports virtual threads) |
| Apache Pekko (fork of Akka) | Actors, streams, clustering |
| Kotlin coroutines | Suspend functions, structured concurrency on the JVM |

## Distributed coordination

| Library | Use |
|---|---|
| **ShedLock** | "Run this `@Scheduled` job on only one node" using a DB/Redis lock row |
| **Redisson** | Redis-backed locks, semaphores, rate limiters (add fencing for correctness-critical work, see [Distributed Concurrency](../system-design/distributed-concurrency.md)) |
| Apache Curator | ZooKeeper recipes: leader election, locks, barriers |
| Hazelcast | Distributed maps, locks (CP subsystem), executors |
| **Quartz**, **JobRunr**, **db-scheduler** | Durable, clustered job scheduling |
| **Confluent Parallel Consumer** | Kafka consumption parallel *within* a partition, with per-key ordering |

## Context propagation and observability

| Library | Use |
|---|---|
| Micrometer + Context Propagation | Metrics, and carrying ThreadLocal context (MDC, tracing) across executors |
| OpenTelemetry Java | Traces; `Context.taskWrapping(executor)` |
| `jdk.jfr` (built in) | Custom JFR events for your own concurrency hotspots |

## When to hand-roll anyway

* Interviews (see the [Coding Interview tab](../coding-interview/index.md)).
* Something tiny and domain-specific, where pulling in a dependency isn't worth it. Even then, test it with Lincheck/jcstress.
