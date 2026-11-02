# CompletableFuture

<span class="level l2">L2</span> <span class="level l3">L3</span>

## Why it existed

Before Java 21, a blocked platform thread was expensive, so you avoided blocking. `CompletableFuture` (CF)
lets you **describe** a pipeline whose stages run when data arrives, so no thread sits waiting.

## Core API map

| Category | Methods |
|---|---|
| Create | `supplyAsync`, `runAsync`, `completedFuture`, `failedFuture` (9), `new CompletableFuture<>()` + `complete` |
| Transform | `thenApply` (map), `thenCompose` (flatMap), `thenAccept`, `thenRun` |
| Combine | `thenCombine`, `thenAcceptBoth`, `applyToEither`, `allOf`, `anyOf` |
| Errors | `exceptionally`, `handle`, `whenComplete`, `exceptionallyCompose` (12) |
| Timeouts (9) | `orTimeout`, `completeOnTimeout` |
| Get | `join` (unchecked), `get` (checked), `getNow`, `resultNow` (19), `state` (19) |

Every `xxx` stage method has `xxxAsync` variants: `xxxAsync(fn)` runs on the common pool, and `xxxAsync(fn, executor)`
runs on your executor. **Always pass your own executor in servers.**

## Fan-out: then (CF) and now (VT)

The scenario is a product page that needs `product`, `price`, and `stock` from three services, each with a timeout and a fallback.

=== "Java 8 / 11 — CompletableFuture"

    ```java
    private final ExecutorService io = Executors.newFixedThreadPool(64);

    public CompletableFuture<ProductPage> page(String id) {
        CompletableFuture<Product> product = CompletableFuture
                .supplyAsync(() -> catalog.get(id), io)
                .orTimeout(300, TimeUnit.MILLISECONDS);                      // Java 9+
        CompletableFuture<Price> price = CompletableFuture
                .supplyAsync(() -> pricing.get(id), io)
                .completeOnTimeout(Price.UNKNOWN, 200, TimeUnit.MILLISECONDS);
        CompletableFuture<Stock> stock = CompletableFuture
                .supplyAsync(() -> inventory.get(id), io)
                .exceptionally(ex -> Stock.UNKNOWN);

        return product.thenCombine(price, (p, pr) -> new Object[]{p, pr})
                      .thenCombine(stock, (arr, s) -> new ProductPage((Product) arr[0], (Price) arr[1], s));
    }
    ```

    Problems: the `Object[]` combination is clumsy, stack traces show `ForkJoinPool` frames instead of your call site,
    a failure in `product` does **not** cancel `price` or `stock`, and the 64-thread pool still blocks 64 OS threads.

=== "Java 21 — virtual threads, blocking style"

    ```java
    private final ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();

    public ProductPage page(String id) throws Exception {
        Future<Product> product = vt.submit(() -> catalog.get(id));     // each call: plain blocking code
        Future<Price>   price   = vt.submit(() -> pricing.get(id));
        Future<Stock>   stock   = vt.submit(() -> inventory.get(id));
        return new ProductPage(
                product.get(300, TimeUnit.MILLISECONDS),
                orDefault(price, Price.UNKNOWN, 200),
                orDefault(stock, Stock.UNKNOWN, 200));
    }
    ```

=== "Java 21–24 — structured concurrency (preview)"

    ```java
    public ProductPage page(String id) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var product = scope.fork(() -> catalog.get(id));
            var price   = scope.fork(() -> pricing.getOr(id, Price.UNKNOWN));
            var stock   = scope.fork(() -> inventory.getOr(id, Stock.UNKNOWN));
            scope.joinUntil(Instant.now().plusMillis(300));   // deadline for the whole fan-out
            scope.throwIfFailed();                            // first failure cancels the siblings
            return new ProductPage(product.get(), price.get(), stock.get());
        }
    }
    ```

## Common pitfalls

1. **Using the common pool for blocking IO.** It has `Ncpu - 1` threads, and blocking starves parallel streams across
   the whole JVM.
2. **Forgetting that `thenApply` may run on the completing thread**, which can be a Netty IO thread.
3. **Swallowed exceptions.** A CF that nobody `join()`s or `handle()`s loses its exception.
4. **`cancel(true)` doesn't interrupt** the running task (`CompletableFuture` ignores `mayInterruptIfRunning`).
5. **`allOf` returns `Void`.** You re-`join()` every future afterwards.

## When CompletableFuture is still the right tool (even on Java 21+)

* You are **in** a reactive/async stack (WebFlux, Vert.x, the async `HttpClient`) and must return a `CompletionStage`.
* You need an **async API contract** (a library method returning `CompletionStage<T>`).
* You want to complete a value from a **callback** (`new CompletableFuture<>()`, then `complete()` from a listener).
* For internal fan-out inside a service, plain blocking code on VTs is simpler.
