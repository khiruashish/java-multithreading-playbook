# Complete Sample Project

`examples/spring-boot-vt` is a self-contained **product-page aggregator** that demonstrates every production pattern in this tab.
It hosts its own simulated downstreams under `/sim`, so it runs with no external dependencies.

## File tree

```text
examples/spring-boot-vt
├── pom.xml                                   Spring Boot 4.1.2, Java 21
├── src/main/resources/application.yml        VTs on, graceful shutdown, bulkheads, deadlines
└── src/main/java/com/playbook/vt
    ├── VirtualThreadsApplication.java
    ├── api/
    │   ├── ProductPageController.java        /api/products/{id}/page, /api/debug/thread, /api/orders/{id}/confirm
    │   ├── SimulatedDownstreamController.java /sim/catalog|pricing|inventory (sleep = IO latency)
    │   └── ErrorHandling.java                RFC 9457 ProblemDetail: 503 / 504 / 404
    ├── application/
    │   ├── ProductPageService.java           fan-out: required + optional subtasks, shared deadline
    │   └── NotificationService.java          @Async + @Scheduled on virtual threads
    ├── client/                               RestClient + JDK HttpClient, one bulkhead each
    ├── concurrency/
    │   ├── FanOut.java                       structured fan-out without preview APIs
    │   ├── Bulkhead.java / BulkheadRegistry  semaphore bulkheads + Micrometer
    │   ├── AdmissionControlFilter.java       max in-flight → 503
    │   ├── PinningMonitor.java               JFR jdk.VirtualThreadPinned → metric + log
    │   └── RequestContext.java               bounded context, ScopedValue-ready
    ├── config/                               properties records, executors, HTTP client
    └── domain/                               records: Product, Price (minor units), Stock, ProductPage
```

## Run it

```bash
cd examples/spring-boot-vt
mvn verify                       # 14 tests: unit + integration + overload
mvn spring-boot:run
```

```bash
curl -s localhost:8080/api/products/p-1/page | jq
curl -s localhost:8080/api/products/slow-1/page | jq .degraded     # ["price"]  (pricing exceeds the deadline)
curl -s localhost:8080/api/products/err-1/page | jq .degraded      # ["stock"]  (inventory fails)
curl -s -i localhost:8080/api/products/missing-1/page              # 404 problem+json
curl -s localhost:8080/api/debug/thread                            # isVirtual: true
curl -s localhost:8080/actuator/metrics/bulkhead.in_use?tag=name:catalog
```

## Compare virtual vs platform threads yourself

```bash
# Terminal 1: virtual threads ON (default)
mvn spring-boot:run
# Terminal 2: load
hey -z 30s -c 2000 http://localhost:8080/api/products/p-1/page      # or: k6, wrk, Gatling
```

```bash
# Then: virtual threads OFF (Tomcat's 200 platform threads)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.threads.virtual.enabled=false"
```

Expect the platform-thread run to plateau at roughly `200 threads / 0.13 s ≈ 1,500 rps`, with latency growing as the
Tomcat queue fills. The VT run keeps scaling until CPU or bulkhead limits kick in.

## Plain-Java examples (no Spring)

| File | Run | Measured on JDK 21, 4 vCPU |
|---|---|---|
| `MillionThreads.java` | `java MillionThreads.java` | 1,000,000 × 1 s sleeps in **9.6 s** (200k in 2.5 s) |
| `PlatformVsVirtualBenchmark.java` | `java PlatformVsVirtualBenchmark.java` | 10k × 100 ms: pool(200) **5,061 ms**, pool(1000) 1,261 ms, VT **224 ms** |
| `FanOutComparison.java` | `java FanOutComparison.java` | 2,000 req × 3-way fan-out: Java 8 CF **2,695 ms**, Java 21 VT **184 ms** |
| `PinningDemo.java` | `java -Djdk.virtualThreadScheduler.parallelism=2 PinningDemo.java` | `synchronized`+sleep **1,014 ms** vs `ReentrantLock` **102 ms** (JDK 21 pinning) |
| `RaceConditionDemo.java` | `java RaceConditionDemo.java` | Unsafe 99,960 / 100,000; all three fixes exact |
| `StructuredConcurrencyDemo.java` | `java --enable-preview --source 21 StructuredConcurrencyDemo.java` | Failure surfaces after 51 ms, not 200 ms (sibling cancelled) |
| `ScopedValueDemo.java` | `java --enable-preview --source 21 ScopedValueDemo.java` | Binding inherited by forked subtasks, rebinding nested |
