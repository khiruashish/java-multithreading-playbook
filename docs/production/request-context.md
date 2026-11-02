# Request Context: ThreadLocal → ScopedValue

<span class="level l3">L3</span>

## The sample's facade (Java 21, no preview)

```java title="RequestContext.java"
--8<-- "examples/spring-boot-vt/src/main/java/com/playbook/vt/concurrency/RequestContext.java"
```

The facade gives you three guarantees:

1. A **bounded lifetime**: the value is cleared in `finally`, so it never leaks to the next request.
2. **Explicit propagation** into forked VTs (`RequestContext.propagate`), because executors don't inherit context.
3. A **one-file migration** to `ScopedValue` on Java 25.

Its test proves both properties:

```java title="RequestContextTest.java"
--8<-- "examples/spring-boot-vt/src/test/java/com/playbook/vt/concurrency/RequestContextTest.java"
```

## Java 25 version (ScopedValue, final)

```java
public final class RequestContext {
    private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    public static <T> T callWith(String id, Callable<T> body) throws Exception {
        return ScopedValue.where(REQUEST_ID, id).call(body::call);
    }
    public static String requestId() { return REQUEST_ID.orElse("none"); }

    /** Plain executors don't inherit bindings. StructuredTaskScope.fork does, automatically. */
    public static <T> Callable<T> propagate(Callable<T> task) {
        if (!REQUEST_ID.isBound()) return task;
        String captured = REQUEST_ID.get();
        return () -> callWith(captured, task);
    }
}
```

## Bridging to logging (MDC)

The controller binds both the context and the MDC. Logback's `%X{requestId}` reads the MDC:

```java title="ProductPageController.java"
--8<-- "examples/spring-boot-vt/src/main/java/com/playbook/vt/api/ProductPageController.java"
```

## Security context

Spring Security stores `SecurityContext` in a `ThreadLocal` (`SecurityContextHolder`), and that works on VTs. For
VTs you create yourself, wrap the executor:

```java
ExecutorService ex = new DelegatingSecurityContextExecutorService(Executors.newVirtualThreadPerTaskExecutor());
```

## Tracing

Micrometer Tracing and OpenTelemetry propagate through `ThreadLocal`s. Wrap the executors you create:

```java
ExecutorService traced = ContextExecutorService.wrap(
        Executors.newVirtualThreadPerTaskExecutor(),
        () -> ContextSnapshotFactory.builder().build().captureAll());
```
