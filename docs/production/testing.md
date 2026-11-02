# Testing Concurrent Code

<span class="level l2">L2</span> → <span class="level l4">L4</span>

| Test type | Tool | What it proves |
|---|---|---|
| Unit: invariants under concurrency | JUnit + VT executor + latches | A limit is never exceeded, nothing is lost |
| Unit: cancellation / deadlines | JUnit + `CountDownLatch` | Siblings are interrupted, fallbacks apply |
| Integration: real server | `@SpringBootTest(RANDOM_PORT)` | Threads are virtual, degradation works end to end |
| Overload | Concurrent client in the test, k6/Gatling outside | Fails fast with 503/504, never hangs |
| Memory-model correctness | **jcstress** | No forbidden interleavings in lock-free code |
| Performance | **JMH** | Throughput and latency, without JIT artefacts |
| Deadlock / leaks | `ThreadMXBean`, JSON thread dumps in CI | No stuck threads after tests |

## Bulkhead test: an invariant under 2,000 VTs

```java title="BulkheadTest.java"
--8<-- "examples/spring-boot-vt/src/test/java/com/playbook/vt/concurrency/BulkheadTest.java"
```

## Fan-out tests: parallelism, cancellation, deadlines

```java title="FanOutTest.java"
--8<-- "examples/spring-boot-vt/src/test/java/com/playbook/vt/concurrency/FanOutTest.java"
```

## Integration and overload tests

```java title="ProductPageIntegrationTest.java"
--8<-- "examples/spring-boot-vt/src/test/java/com/playbook/vt/api/ProductPageIntegrationTest.java"
```

## Tips for deterministic concurrency tests

1. **Coordinate with latches, not sleeps.** `started.await()` before triggering a failure makes the test deterministic.
2. **Assert ranges for timing** (`< 800 ms` parallel vs 900 ms sequential) and leave CI headroom.
3. **Hammer the code**: 1,000+ VTs are cheap and expose races far more often than 4 platform threads.
4. **Check cleanup**: `bulkhead.inUse() == 0` after the run proves permits are always released.
5. **jcstress** for anything lock-free ([Benchmarking](../performance/benchmarking.md)).
