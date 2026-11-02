# L4: 25 Worked Examples

<span class="level l1">L1</span> → <span class="level l3">L3</span>

Each example shows the **Java 8 way**, the **Java 21+ way**, and the **benefit**. Imports are omitted for brevity.
All examples assume `var vt = Executors.newVirtualThreadPerTaskExecutor()` is created in a `try`-with-resources block, unless noted.

---

## Beginner (1–8)

### 1. Run a background task

=== "Java 8"
    ```java
    new Thread(() -> sendWelcomeEmail(user)).start();
    ```
=== "Java 21"
    ```java
    Thread.ofVirtual().name("welcome-email").start(() -> sendWelcomeEmail(user));
    ```
**Benefit:** about 1,000× cheaper to create, and named for thread dumps.

### 2. Wait for several tasks

=== "Java 8"
    ```java
    Thread a = new Thread(this::loadA), b = new Thread(this::loadB);
    a.start(); b.start(); a.join(); b.join();
    ```
=== "Java 21"
    ```java
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
        ex.submit(this::loadA);
        ex.submit(this::loadB);
    } // close() waits for both
    ```
**Benefit:** join semantics come for free from `close()`.

### 3. Fetch 1,000 URLs concurrently

=== "Java 8"
    ```java
    ExecutorService pool = Executors.newFixedThreadPool(50);       // 1000/50 = 20 "waves"
    List<Future<String>> fs = new ArrayList<>();
    for (URI u : uris) fs.add(pool.submit(() -> httpGet(u)));
    for (Future<String> f : fs) bodies.add(f.get());
    pool.shutdown();
    ```
=== "Java 21"
    ```java
    HttpClient client = HttpClient.newBuilder().executor(vt).build();  // client's internal tasks run on VTs too
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Future<String>> fs = uris.stream()
            .map(u -> ex.submit(() -> client.send(HttpRequest.newBuilder(u).build(),
                                                  BodyHandlers.ofString()).body()))
            .toList();
        bodies = fs.stream().map(Future::resultNow).toList();  // safe after close()
    }
    ```
**Benefit:** total time is roughly the **slowest** response, instead of 20 × the average response.

### 4. Sleep without wasting a thread

```java
Thread.ofVirtual().start(() -> {
    while (true) {
        pollAndProcess();
        try { Thread.sleep(Duration.ofSeconds(5)); } catch (InterruptedException e) { return; }
    }
});
```
**Benefit:** 10,000 such pollers cost only a few MB. With platform threads they would cost 10 GB of stack reservations.

### 5. Read 500 files in parallel

```java
try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
    Map<Path, Future<Long>> lines = files.stream()
        .collect(toMap(p -> p, p -> ex.submit(() -> { try (var s = Files.lines(p)) { return s.count(); } })));
}
```
!!! note
    File IO **captures** the carrier (the JDK compensates with extra carriers). It still works, and it's simpler than
    NIO2 `AsynchronousFileChannel`, but the gain is smaller than for network IO.

### 6. Timeout a single task

```java
Future<Report> f = vt.submit(() -> reportService.build(id));
try {
    return f.get(2, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    f.cancel(true);                    // interrupts the VT → its blocking IO aborts
    return Report.partial(id);
}
```

### 7. Background job with a result callback

=== "Java 8"
    ```java
    CompletableFuture.supplyAsync(() -> generatePdf(order), pool)
                     .thenAccept(pdf -> mailer.send(order.email(), pdf))
                     .exceptionally(ex -> { log.error("pdf failed", ex); return null; });
    ```
=== "Java 21"
    ```java
    Thread.ofVirtual().name("pdf-", order.id()).start(() -> {
        try {
            mailer.send(order.email(), generatePdf(order));       // just sequential code
        } catch (Exception ex) {
            log.error("pdf failed", ex);
        }
    });
    ```
**Benefit:** ordinary `try/catch`, stack traces that point to *your* code, and you can step through it in a debugger.

### 8. Distinguish VT from platform thread at runtime

```java
if (Thread.currentThread().isVirtual()) metrics.counter("on.vt").increment();
```

---

## Intermediate (9–17)

### 9. Thread-per-connection TCP server

See [L1 echo server](01-basics.md#example-an-echo-server-old-vs-new). It scales to 100k+ connections with blocking code.

### 10. JDBC fan-out with a bounded connection pool

```java
HikariDataSource ds = ...;  // maximumPoolSize = 20 — THE real limit
try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<Customer>> fs = ids.stream()
        .map(id -> ex.submit(() -> customerDao.find(ds, id)))   // 5000 VTs, 20 connections
        .toList();
}
```
VTs queue on Hikari's `getConnection()` (it parks with a timeout via `connectionTimeout`). There's no thread
explosion, and the database sees at most 20 sessions. Set `connectionTimeout` so requests fail fast instead of piling up.

### 11. Semaphore bulkhead per downstream

```java
private final Semaphore paymentsPermits = new Semaphore(50);

PaymentResult charge(Payment p) throws Exception {
    if (!paymentsPermits.tryAcquire(200, TimeUnit.MILLISECONDS)) throw new BulkheadFullException("payments");
    try { return paymentsClient.charge(p); }
    finally { paymentsPermits.release(); }
}
```
**Benefit:** the thread pool used to cap concurrency implicitly. With VTs you cap it **explicitly** per dependency, so a
slow payments service can't consume all of your capacity.

### 12. Producer / consumer pipeline

```java
BlockingQueue<Line> raw = new ArrayBlockingQueue<>(1_000);
BlockingQueue<Record> parsed = new ArrayBlockingQueue<>(1_000);
try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
    ex.submit(() -> { reader.forEach(l -> put(raw, l)); put(raw, Line.EOF); return null; });
    ex.submit(() -> { for (Line l; (l = raw.take()) != Line.EOF; ) parsed.put(parse(l)); parsed.put(Record.EOF); return null; });
    ex.submit(() -> { for (Record r; (r = parsed.take()) != Record.EOF; ) sink.write(r); return null; });
}
```
Bounded queues provide the backpressure, and blocking `put` and `take` cost nothing on VTs.

### 13. Scatter-gather with "first successful wins"

=== "Java 8"
    ```java
    CompletableFuture<Quote> any = CompletableFuture.anyOf(
            supplyAsync(() -> vendorA.quote(item), pool),
            supplyAsync(() -> vendorB.quote(item), pool))
        .thenApply(Quote.class::cast);   // anyOf completes on first *completion*, even an exceptional one
    ```
=== "Java 21"
    ```java
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
        Quote q = ex.invokeAny(List.of(() -> vendorA.quote(item), () -> vendorB.quote(item)),
                               500, TimeUnit.MILLISECONDS);   // first SUCCESS; losers are cancelled
    }
    ```
**Benefit:** `invokeAny` skips failures and cancels the losers, so you don't have to write that error handling yourself.

### 14. Parallel batch processing with progress

```java
LongAdder done = new LongAdder();
try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
    for (Invoice inv : invoices) {
        ex.submit(() -> { emailInvoice(inv); done.increment(); });
    }
    ScheduledExecutorService progress = Executors.newSingleThreadScheduledExecutor();
    progress.scheduleAtFixedRate(() -> log.info("sent {}/{}", done.sum(), invoices.size()), 1, 1, SECONDS);
    // ex.close() at end of the block waits for all emails; then stop the progress reporter
    ex.shutdown(); ex.awaitTermination(1, TimeUnit.HOURS); progress.shutdownNow();
}
```

### 15. Retry with backoff (blocking style)

```java
<T> T retry(Callable<T> call, int attempts, Duration base) throws Exception {
    for (int i = 1; ; i++) {
        try { return call.call(); }
        catch (IOException e) {
            if (i == attempts) throw e;
            long jitter = ThreadLocalRandom.current().nextLong(base.toMillis());
            Thread.sleep(base.multipliedBy(1L << (i - 1)).plusMillis(jitter));   // cheap on a VT
        }
    }
}
```
**Benefit:** no `ScheduledExecutorService` and no `CompletableFuture.delayedExecutor` chains. Sleeping is free.

### 16. Request-scoped context with ScopedValue

```java
static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

void handle(Request r) {
    ScopedValue.where(REQUEST_ID, r.id()).run(() -> service.process(r));   // visible to all callees
}
void deepInsideService() { log.info("[{}] processing", REQUEST_ID.get()); }
```
Final in Java 25. Needs `--enable-preview` on 21–24. See [Scoped Values](../modern/scoped-values.md).

### 17. Deadline across several calls

```java
Instant deadline = Instant.now().plusMillis(800);
User u      = userClient.get(id, remaining(deadline));
Orders o    = orderClient.recent(id, remaining(deadline));
Recs recs   = recClient.forUser(id, remaining(deadline));

static Duration remaining(Instant d) {
    Duration left = Duration.between(Instant.now(), d);
    if (left.isNegative() || left.isZero()) throw new DeadlineExceededException();
    return left;
}
```

---

## Advanced (18–25)

### 18. Structured fan-out with fail-fast (JDK 21–24 preview API)

```java
OrderView view(long orderId) throws Exception {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        Subtask<Order> order     = scope.fork(() -> orders.get(orderId));
        Subtask<Payment> payment = scope.fork(() -> payments.forOrder(orderId));
        Subtask<Shipment> ship   = scope.fork(() -> shipping.forOrder(orderId));
        scope.join().throwIfFailed(IllegalStateException::new);
        return new OrderView(order.get(), payment.get(), ship.get());
    }
}
```

### 19. Same, with the Java 25 API (fifth preview)

```java
OrderView view(long orderId) throws InterruptedException {
    try (var scope = StructuredTaskScope.open()) {                 // default Joiner: all must succeed
        Subtask<Order> order     = scope.fork(() -> orders.get(orderId));
        Subtask<Payment> payment = scope.fork(() -> payments.forOrder(orderId));
        Subtask<Shipment> ship   = scope.fork(() -> shipping.forOrder(orderId));
        scope.join();                                             // throws FailedException on first failure
        return new OrderView(order.get(), payment.get(), ship.get());
    }
}
```

### 20. Hedged request (tail-latency killer)

```java
<T> T hedged(Callable<T> primary, Callable<T> backup, Duration hedgeAfter) throws Exception {
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
        var cs = new ExecutorCompletionService<T>(ex);
        cs.submit(primary);
        Future<T> first = cs.poll(hedgeAfter.toMillis(), TimeUnit.MILLISECONDS);
        if (first == null) {                       // primary is slow: fire the backup
            cs.submit(backup);
            first = cs.take();
        }
        T result = first.get();
        ex.shutdownNow();                          // cancel whichever is still running
        return result;
    }
}
```
**Benefit:** cuts p99 latency dramatically for idempotent reads, at the cost of a few percent extra load.

### 21. Rate-limited crawler (permits + per-host limits)

```java
Semaphore global = new Semaphore(200);
ConcurrentHashMap<String, Semaphore> perHost = new ConcurrentHashMap<>();

void crawl(URI uri) throws InterruptedException {
    Semaphore host = perHost.computeIfAbsent(uri.getHost(), h -> new Semaphore(4));
    global.acquire();
    try {
        host.acquire();
        try { fetchAndEnqueueLinks(uri); } finally { host.release(); }
    } finally { global.release(); }
}
```

### 22. Kafka-style partition workers: order per key, parallel across keys

```java
// One VT per partition key keeps per-key ordering while running all keys in parallel
ConcurrentHashMap<String, BlockingQueue<Event>> lanes = new ConcurrentHashMap<>();
void dispatch(Event e) {
    lanes.computeIfAbsent(e.key(), k -> {
        var q = new LinkedBlockingQueue<Event>(10_000);
        Thread.ofVirtual().name("lane-" + k).start(() -> drain(k, q));
        return q;
    }).add(e);
}
void drain(String key, BlockingQueue<Event> q) {
    try {
        for (Event e; (e = q.poll(30, SECONDS)) != null; ) handle(e);
    } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    finally { lanes.remove(key, q); }       // idle lane retires itself
}
```
!!! warning "Race on retirement"
    An event added between `poll` returning `null` and `lanes.remove` is lost. Use the race-free `KeyedLanes` in
    [Case Studies](../system-design/case-studies.md#case-3-ordered-parallel-event-processing) for production.

### 23. Mixing CPU and IO correctly

```java
ExecutorService cpu = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                                                   Thread.ofPlatform().name("cpu-", 0).factory());

Image handle(URI src) throws Exception {
    byte[] raw = http.download(src);                       // IO on the VT
    return cpu.submit(() -> resize(raw, 800, 600)).get();  // CPU on a bounded platform pool; VT parks
}
```
**Benefit:** CPU-heavy work never monopolizes the VT carriers, so IO-bound VTs keep flowing.

### 24. Graceful shutdown of a VT worker fleet

```java
ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
    workers.shutdown();                                         // stop accepting
    try {
        if (!workers.awaitTermination(25, SECONDS)) workers.shutdownNow();  // interrupt stragglers
    } catch (InterruptedException e) { workers.shutdownNow(); }
}));
```

### 25. Benchmark: platform pool vs VTs (simple harness)

```java
static Duration run(ExecutorService ex, int tasks, Duration ioLatency) throws Exception {
    long t0 = System.nanoTime();
    try (ex) {
        for (int i = 0; i < tasks; i++) ex.submit(() -> { Thread.sleep(ioLatency); return null; });
    }
    return Duration.ofNanos(System.nanoTime() - t0);
}
// Measured, JDK 21, 4 vCPU:
// run(Executors.newFixedThreadPool(200),  10_000, 100ms) → 5,061 ms  (10000/200 × 100ms)
// run(Executors.newFixedThreadPool(1000), 10_000, 100ms) → 1,261 ms
// run(Executors.newVirtualThreadPerTaskExecutor(), 10_000, 100ms) →   224 ms
```
Runnable version: `examples/plain-java/PlatformVsVirtualBenchmark.java`. Use JMH for real numbers
([Benchmarking](../performance/benchmarking.md)).
