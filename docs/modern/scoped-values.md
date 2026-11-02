# Scoped Values

<span class="level l3">L3</span> — **final in Java 25 (JEP 506)**, preview in 21–24.

## Why replace ThreadLocal?

| Problem with `ThreadLocal` | `ScopedValue` answer |
|---|---|
| Mutable: any callee can `set()` | Immutable within a binding |
| Unbounded lifetime: leaks if you forget `remove()` | Lifetime = the `run`/`call` block |
| `InheritableThreadLocal` copies maps on every child thread | Children of a `StructuredTaskScope` share the parent's bindings for free |
| Hard to reason about data flow | Data flow is visible: `where(K, v).run(...)` |
| Slow lookups with many entries | JIT-friendly lookups with a per-thread cache |

## Basic usage

```java
public final class RequestContext {
    public static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();
    public static final ScopedValue<Principal> USER    = ScopedValue.newInstance();
}

// At the edge (filter / controller advice)
ScopedValue.where(RequestContext.REQUEST_ID, requestId)
           .where(RequestContext.USER, principal)
           .run(() -> orderService.placeOrder(cmd));

// Anywhere deeper — no parameter threading needed
String rid = RequestContext.REQUEST_ID.get();                 // NoSuchElementException if unbound
String rid2 = RequestContext.REQUEST_ID.orElse("unknown");
boolean bound = RequestContext.REQUEST_ID.isBound();
```

Returning a value:

```java
Receipt r = ScopedValue.where(USER, principal).call(() -> checkout.run(cart));   // call() for a result
```

## Rebinding for a nested scope

```java
ScopedValue.where(USER, admin).run(() -> {
    audit();                                            // sees admin
    ScopedValue.where(USER, systemUser).run(this::job); // nested: sees systemUser
    audit();                                            // sees admin again
});
```

## Inheritance with structured concurrency

```java
ScopedValue.where(REQUEST_ID, "req-42").run(() -> {
    try (var scope = StructuredTaskScope.open()) {
        scope.fork(() -> log("[{}] fetch user", REQUEST_ID.get()));   // "req-42", no copy cost
        scope.fork(() -> log("[{}] fetch cart", REQUEST_ID.get()));
        scope.join();
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
});
```

!!! note "Plain executors do NOT inherit scoped values"
    `executor.submit(...)` runs in a new thread with **no** bindings. Only `StructuredTaskScope.fork` inherits them.
    Capture the value and re-bind it explicitly if you need it inside an executor task.

## Then and now: MDC / request id

=== "Java 8 (ThreadLocal + finally)"

    ```java
    public void doFilter(...) {
        MDC.put("requestId", id);
        try { chain.doFilter(req, res); }
        finally { MDC.remove("requestId"); }   // forget this → leak to the next request on a pooled thread
    }
    ```

=== "Java 25 (ScopedValue)"

    ```java
    public void doFilter(...) {
        ScopedValue.where(REQUEST_ID, id).run(() -> {
            try { chain.doFilter(req, res); }
            catch (IOException | ServletException e) { throw new RuntimeException(e); }
        });   // nothing to clean up
    }
    ```

!!! info "Logging frameworks"
    SLF4J/Logback MDC is still `ThreadLocal`-based. A common bridge is to bind a `ScopedValue` and **also** put the id in the
    MDC inside the scope, or to write a custom Logback converter that reads the scoped value. See
    [Request Context](../production/request-context.md).
