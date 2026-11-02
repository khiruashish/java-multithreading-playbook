# spring-boot-vt

Production-style product-page aggregator on **Spring Boot 4.1.2 + Java 21 virtual threads** (runs unchanged on 24/25).

* Virtual-thread request handling, `@Async`, `@Scheduled` (`spring.threads.virtual.enabled=true`)
* `FanOut`: structured fan-out with a shared deadline, sibling cancellation, required/optional subtasks — no preview APIs
* Semaphore bulkheads per downstream + Micrometer metrics; admission-control filter (503 load shedding)
* JFR `jdk.VirtualThreadPinned` streaming monitor → metric + log
* Bounded platform pool for CPU work; JDK `HttpClient` with timeouts
* RFC 9457 problem details for 503 / 504 / 404
* `RequestContext` with bounded lifetime, ready to swap to `ScopedValue` on Java 25

```bash
mvn verify            # 14 tests: Bulkhead, FanOut, RequestContext, integration + 300-request overload test
mvn spring-boot:run
curl -s localhost:8080/api/products/p-1/page
curl -s localhost:8080/api/products/slow-1/page   # pricing exceeds deadline → degraded: ["price"]
curl -s localhost:8080/api/debug/thread           # isVirtual: true
```

Explained in the playbook under **Production Code (Spring Boot)**.
