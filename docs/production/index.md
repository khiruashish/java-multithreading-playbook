# Production Code: Java 21/24/25 in Spring Boot

<span class="level l2">L2</span> → <span class="level l4">L4</span>

Every snippet on this tab is **included directly from a compiling, tested project**:
[`examples/spring-boot-vt`](https://github.com/khiruashish/java-multithreading-playbook/tree/main/examples/spring-boot-vt)
(Spring Boot **4.1.2**, Java **21** baseline, runs unchanged on 24/25). `mvn verify` runs 14 tests, including a
300-concurrent-request overload test.

## Architecture of the sample service

```mermaid
flowchart LR
    C[Client] -->|GET /api/products/id/page| AF[AdmissionControlFilter<br/>Semaphore 5000]
    AF --> T[Tomcat<br/>virtual thread per request]
    T --> PC[ProductPageController<br/>MDC + RequestContext]
    PC --> S[ProductPageService]
    S --> FO{FanOut scope<br/>deadline 800 ms}
    FO -->|required| CC[CatalogClient] --> BH1[Bulkhead catalog=200] --> D1[(catalog)]
    FO -->|optional| PR[PricingClient] --> BH2[Bulkhead pricing=100] --> D2[(pricing)]
    FO -->|optional| IC[InventoryClient] --> BH3[Bulkhead inventory=100] --> D3[(inventory)]
    S -.CPU work.-> CPU[cpuExecutor<br/>platform pool = #cores]
    JFR[PinningMonitor<br/>JFR stream] -.-> M[(Micrometer / Prometheus)]
    BH1 & BH2 & BH3 -.metrics.-> M
```

## Pages

| Page | What it covers |
|---|---|
| [Enabling Virtual Threads](spring-boot-setup.md) | One property, and what it switches. Tomcat/Jetty, `@Async`, `@Scheduled`, messaging. Version matrix |
| [Fan-out Aggregator](fan-out-service.md) | Required vs optional subtasks, shared deadline, sibling cancellation. Java 8 vs 21 |
| [Async, Scheduling & Messaging](async-scheduling-messaging.md) | `@Async`, `@Scheduled`, Kafka/RabbitMQ listeners, `TaskDecorator` |
| [Database & Connection Pools](database.md) | HikariCP sizing with Little's Law, transactions, JPA on VTs |
| [Resilience](resilience.md) | Admission control, bulkheads, timeouts, Resilience4j, hedging |
| [Request Context](request-context.md) | ThreadLocal → ScopedValue, MDC, tracing propagation |
| [Observability](observability.md) | Pinning monitor, metrics, dashboards, alerts |
| [Testing](testing.md) | Unit tests for concurrency primitives, overload tests, jcstress |
| [Graceful Shutdown & Ops](shutdown-ops.md) | SIGTERM, K8s probes, container CPU, JVM flags |
| [Complete Sample Project](sample-project.md) | File tree, run instructions, load-test recipe |
