# Interview: 10–15 Years (Staff / Principal / Architect)

<span class="level l4">L4</span>

At this level, interviewers want **judgement backed by numbers, failure analysis, and organizational impact**.

## Platform strategy

??? question "1. Your org has 80 Java 11 services, some on WebFlux. What's your concurrency strategy for the next two years?"
    Standardize on JDK 25 LTS and Spring Boot 3.5+/4.x. Make MVC + virtual threads the golden path for request/response services, and keep WebFlux for
    streaming, the gateway, and healthy reactive services. Give the platform template admission control, bulkheads, JFR pinning alerts, and lint rules. Migrate
    by tiers (IO-heavy aggregators first), toggle by configuration, and use canaries and dashboards. Measure the effect on cost and latency, and roll back on regressions.

??? question "2. How do you decide between virtual threads and reactive for a new service?"
    Ask: is there streaming or backpressure across boundaries? Is there an existing reactive ecosystem? What's the team's skill set? What do the drivers support? What's CPU per request? Default to VTs.
    Choose reactive for streams, SSE/WebSocket fan-out, and the gateway tier, and justify it with a prototype benchmark.

??? question "3. What guardrails would you put in a service template for VTs?"
    An admission filter, a semaphore bulkhead library with metrics, mandatory timeouts in HTTP/DB client factories, a CPU executor for heavy work,
    a JFR pinning monitor, a context-propagation wrapper, ArchUnit rules (no VT pooling, no `ThreadLocal` fields outside the allow-list, no `parallelStream`
    in IO code), and load-test gates.

??? question "4. How do you justify the migration cost to leadership?"
    A capacity model: thread-bound vs CPU-bound ceilings per service, pods saved, and incident classes removed (pool exhaustion). Add developer
    productivity (less code, simpler debugging, onboarding) and a pilot with before/after metrics. Present ranges, not promises.

??? question "5. Which services gain the least from VTs?"
    CPU-bound services (ML scoring, image processing), services already at their DB limits, and low-concurrency batch jobs. The gain is roughly the
    ratio of the thread ceiling to the other ceilings.

## Capacity and performance

??? question "6. Walk through capacity planning for a 5k-rps checkout service using Little's Law."
    In-flight = λ × latency. DB connections = λ × DB time per request (with headroom), checked against the DB's `max_connections` across pods. Downstream permits = λ × latency. CPU =
    λ × CPU time / target utilization, which sets the pod count. Then validate with load tests and add headroom for retries and failover.

??? question "7. A downstream slows from 100 ms to 5 s. Compare the platform-thread and VT services' behaviour."
    Platform: thread exhaustion makes the entire service fail (cascade). VT without limits: in-flight work grows 50×, along with heap, sockets, and
    connection pool exhaustion, which can also cascade. VT with bulkheads and timeouts: only that path fails fast, so the blast radius is contained.

??? question "8. How do you set timeout values across a call chain?"
    Budget from the edge SLO downward: each hop's timeout is smaller than its caller's, account for retries (budget ≥ attempts × timeout + backoff), and
    propagate deadlines so downstreams stop working on requests that have already been abandoned.

??? question "9. How do retries interact with overload?"
    Retries amplify load (retry storms). Use retry budgets (a percentage of traffic), jittered backoff, circuit breakers, idempotency keys, and only retry
    idempotent operations.

??? question "10. When would you use hedged requests, and what are the risks?"
    For tail-latency-sensitive idempotent reads with variable latency. The risks are extra load (cap it at a few percent), non-idempotent side effects, and making things worse during an
    overload (disable hedging when error rates rise).

## Failure isolation and resilience

??? question "11. Design the resilience layers for a service calling five dependencies."
    Edge admission control → per-dependency bulkhead + timeout + circuit breaker + retry budget → resource pools. Add fallbacks for optional data, plus
    degraded flags in the response, and dashboards and alerts per layer.

??? question "12. How do you prevent one tenant from starving others in a multi-tenant service?"
    Per-tenant semaphores and rate limits (weighted), fair queuing, separate pools or cells for big tenants, and quotas at the gateway. Make it observable
    per tenant.

??? question "13. What's a cell-based architecture, and how does it relate to concurrency limits?"
    Partition users into independent stacks (cells), each with bounded capacity. Limits and failures stay within a cell, which reduces the blast radius.
    Concurrency limits are then sized per cell.

??? question "14. How do you handle backpressure end to end, from mobile client to database?"
    Client rate limits and retries with jitter, the gateway's rate limiting, service admission control (429/503 with `Retry-After`), bounded queues
    and semaphores inside, DB pool limits, and bounded queue consumers with lag-based autoscaling.

## Distributed concurrency

??? question "15. How do you guarantee no double charge in payments?"
    Idempotency keys stored with a unique constraint, a state machine with conditional updates, an outbox for events, reconciliation jobs, and
    provider-side idempotency keys.

??? question "16. Distributed lock with Redis: is it safe?"
    It's fine for efficiency (avoiding duplicate work). For correctness, it isn't safe on its own, because of GC pauses, clock drift, and lease expiry. Use fencing tokens verified by
    the storage layer, or a consensus system (ZooKeeper or etcd) plus fencing.

??? question "17. Saga vs 2PC?"
    2PC blocks while holding locks, the coordinator is a single point of failure, and many systems don't support it. A saga is a sequence of local transactions with compensations.
    It's eventually consistent, needs idempotent steps and careful design of the compensation semantics, and is usually orchestrated for complex flows.

??? question "18. How do you keep an aggregate consistent under concurrent commands across pods?"
    Optimistic concurrency on the aggregate version, a single writer per aggregate (partition by key), or DB constraints. Avoid in-JVM locks.

??? question "19. Explain exactly-once in Kafka, and its limits."
    Idempotent producer + transactions + `read_committed` consumers give exactly-once **within Kafka**. Side effects outside Kafka need
    idempotent consumers or the outbox/inbox pattern.

??? question "20. How do you process a work queue in PostgreSQL across many workers?"
    `SELECT ... FOR UPDATE SKIP LOCKED LIMIT n` within a short transaction, a visibility timeout or lease, retries with a dead-letter table, and an index on status/run_at.

## Leadership and migration

??? question "21. Describe a concurrency incident you'd expect, and how you'd run the postmortem."
    For example, a pool exhaustion cascade. Build a timeline from metrics and thread dumps, find the root cause (missing timeout or bulkhead), determine contributing factors
    (retry storm), and take actions (template change, alert, load test), all blameless.

??? question "22. How do you roll out JDK 21 → 25 across the fleet safely?"
    Upgrade the build first (compile at the new release), update dependencies, canary by tier, compare GC and latency, keep the toggles, and set a deadline policy for laggards.
    Use the JEP 491 benefit to drop `ReentrantLock` workarounds gradually.

??? question "23. Would you adopt structured concurrency (preview) in production?"
    Only behind an internal facade, with `--enable-preview` managed centrally and an awareness that the API changes between releases (it did between 24 and 25). A
    non-preview VT fallback inside the facade is pragmatic.

??? question "24. How do you train 200 engineers on concurrency?"
    A leveled curriculum (this playbook), golden-path code, code review checklists, internal talks with real incidents, and katas (race,
    deadlock, pinning).

??? question "25. What metrics define 'concurrency health' for a service?"
    In-flight vs limit, bulkhead saturation and rejections, pool waiters, pinned events, p99 vs SLO, retry rate, timeouts per dependency, and heap trend.

## Deep technical (architect still expected to know)

??? question "26. Why might a VT service have higher memory usage than expected?"
    Missing timeouts (parked VTs accumulate), large per-request object graphs, `ThreadLocal` caches, deep stacks (big frames), and unbounded fan-out.

??? question "27. What happens when the VT scheduler's `maxPoolSize` is reached?"
    No more compensating carriers are added. Further capturing operations reduce effective parallelism, and VTs queue. JFR may show
    `VirtualThreadSubmitFailed` in extreme cases.

??? question "28. Can VTs cause a deadlock that platform threads wouldn't?"
    The same lock-ordering deadlocks occur. On JDK 21–23, pinning can starve carriers: all carriers are pinned, waiting for a lock held by an unmounted VT
    that can't get a carrier. That's carrier-starvation deadlock, which JEP 491 largely fixes.

??? question "29. How does GC interact with millions of VTs?"
    Stack chunks are heap objects, so they're scanned and moved by GC. Parked VTs' stacks are roots only if the VT is reachable. Generational ZGC handles
    many short-lived chunks well.

??? question "30. What's next for Java concurrency beyond 25?"
    Structured concurrency finalization, possibly custom schedulers, better observability (scheduler MXBean in 24), and continued work on removing pinning from
    native and class-init paths.

## Open-ended design prompts

??? question "31. Design a notification platform sending 50M messages/day over multiple channels."
    See [System Design Rounds](system-design-rounds.md#2-notification-platform).

??? question "32. Design a rate limiter service for a public API."
    See [System Design Rounds](system-design-rounds.md#4-distributed-rate-limiter).

??? question "33. Design the inventory reservation for a flash sale."
    See [Case Studies](../system-design/case-studies.md#case-2-flash-sale-inventory-contention).

??? question "34. Design a job scheduler with exactly-once execution semantics."
    Use durable job rows with a unique (job, schedule) key, workers claiming with `SKIP LOCKED` and leases plus fencing, idempotent handlers, and a dead-letter table.
    "Exactly-once" really means at-least-once execution with idempotent handlers.

??? question "35. Design a real-time order-tracking feed for 1M concurrent app users."
    WebSocket/SSE gateway (reactive or VT-per-connection), a pub/sub fan-out (Redis Streams or Kafka) partitioned by order, backpressure that drops intermediate
    location updates, connection-draining on deploys, and autoscaling on connection count.

??? question "36. How would you design a thread-safe, in-memory cache with refresh-ahead for 100k rps?"
    Caffeine (W-TinyLFU, async refresh) with `refreshAfterWrite`, bounded size, stampede protection (one loader per key), metrics, and a snapshot for config.

??? question "37. Your team proposes moving a reactive gateway to VTs. How do you evaluate it?"
    Benchmark at production RPS and connection counts with both. Compare p99, CPU, and memory, check feature parity (filters, streaming), weigh the operational
    familiarity, and decide on evidence.

??? question "38. How do you prevent thundering herds after a cache flush or restart?"
    Request coalescing (single-flight per key), jittered TTLs, warm-up before taking traffic, and serving stale data while revalidating.

??? question "39. What's the role of immutability in your platform's concurrency strategy?"
    Records and immutable DTOs by default, copy-on-write snapshots for config, and events as immutable facts. That removes most shared-mutable-state bugs.

??? question "40. Summarize what changed in Java concurrency from 8 to 25 in one minute."
    8: composable async (CF), better primitives (StampedLock, LongAdder). 9: Flow, VarHandle, CF timeouts. 19–21: virtual threads (final in 21) and AutoCloseable
    executors. 24: no pinning on `synchronized`. 25: scoped values final, structured concurrency reshaped (preview). Net effect: simple blocking code is
    scalable again, and limits move from thread pools to explicit resource bulkheads.
