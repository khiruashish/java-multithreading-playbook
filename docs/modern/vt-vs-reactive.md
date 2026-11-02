# Virtual Threads vs Reactive

<span class="level l3">L3</span> <span class="level l4">L4</span>

## Same problem, two answers

Both solve **"don't waste an OS thread while waiting"**:

* **Reactive** (WebFlux / Reactor, RxJava, Vert.x): never block. Describe the pipeline as operators, and let a few event-loop
  threads run callbacks when data arrives.
* **Virtual threads**: block freely. The JVM parks the cheap thread and reuses the carrier.

=== "Reactive (WebFlux)"

    ```java
    public Mono<OrderView> view(long id) {
        return orderRepo.findById(id)                                   // R2DBC
            .switchIfEmpty(Mono.error(new NotFound(id)))
            .flatMap(o -> Mono.zip(
                    paymentClient.get().uri("/payments/{id}", id).retrieve().bodyToMono(Payment.class)
                                 .timeout(Duration.ofMillis(300))
                                 .onErrorReturn(Payment.UNKNOWN),
                    shippingClient.get().uri("/shipments/{id}", id).retrieve().bodyToMono(Shipment.class))
                .map(t -> new OrderView(o, t.getT1(), t.getT2())));
    }
    ```

=== "Virtual threads (MVC)"

    ```java
    public OrderView view(long id) throws Exception {
        Order o = orderRepo.findById(id).orElseThrow(() -> new NotFound(id));  // JPA/JDBC
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Payment> p  = ex.submit(() -> paymentClient.get(id));       // RestClient
            Future<Shipment> s = ex.submit(() -> shippingClient.get(id));
            return new OrderView(o, orDefault(p, Payment.UNKNOWN, 300), s.get());
        }
    }
    ```

## Comparison matrix

| Dimension | Reactive | Virtual threads |
|---|---|---|
| Throughput (IO-bound) | Excellent | Excellent (within ~5%) |
| Memory per in-flight request | Lowest | Low (stack chunks) |
| Code readability | Operator chains | Plain sequential code |
| Stack traces / debugging | Hard (assembly-time vs runtime traces) | Natural |
| Profiling | Hard to attribute | Normal |
| Learning curve | Steep | None beyond normal Java |
| Library ecosystem | Needs reactive drivers (R2DBC, reactive Mongo/Redis) | Any blocking library (JDBC, JPA, ...) |
| Backpressure | Built in (Reactive Streams `request(n)`) | Manual: bounded queues and semaphores |
| Streaming (SSE, infinite streams) | First-class | Possible, more manual |
| Cancellation | Built-in (`dispose`) | Interruption / structured concurrency |
| Context propagation | Reactor `Context` | `ThreadLocal` / `ScopedValue` |
| Transactions | Reactive TX, limited | Classic `@Transactional` |

## Recommendation

* **Default to virtual threads** for request/response microservices.
* **Keep reactive** where you need streaming, backpressure across network boundaries, or where an existing reactive
  codebase is healthy (don't rewrite just because of VTs).
* **Gateways** (Spring Cloud Gateway) are reactive by design. That's fine. There's also a Spring Cloud Gateway **Server MVC**
  variant that runs on VTs.
