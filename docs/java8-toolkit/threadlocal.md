# ThreadLocal

<span class="level l2">L2</span> <span class="level l3">L3</span>

## Two different jobs

| Use | Example | Virtual-thread verdict |
|---|---|---|
| **Per-thread cache** of an expensive, non-thread-safe object | `SimpleDateFormat`, `MessageDigest`, Kryo, byte buffers | ❌ **Bad.** One copy per VT means millions of copies and no reuse |
| **Implicit context** passing | MDC request id, security context, transaction | ⚠️ Works, but mutable, leak-prone, and inheritance is expensive → use `ScopedValue` |

## Classic usage and its leak

```java
private static final ThreadLocal<SimpleDateFormat> FMT =
        ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

// In a pooled-thread server, ALWAYS clean up context-style ThreadLocals:
try {
    CURRENT_USER.set(user);
    chain.doFilter(req, res);
} finally {
    CURRENT_USER.remove();            // otherwise the next request on this pooled thread sees the old user
}
```

## Replacements

| Old | New |
|---|---|
| `ThreadLocal<SimpleDateFormat>` | `DateTimeFormatter` (immutable, thread-safe, Java 8) |
| `ThreadLocal<MessageDigest>` | Create one per use (cheap), or use a small object pool |
| `ThreadLocal<byte[]>` buffer | Allocate per call (TLAB allocation is cheap), or use a bounded pool |
| `ThreadLocal<RequestContext>` | `ScopedValue<RequestContext>` (final in Java 25) — see [Scoped Values](../modern/scoped-values.md) |
| `InheritableThreadLocal` | `ScopedValue` + `StructuredTaskScope` (bindings are inherited by forked subtasks at no copying cost) |

## Virtual-thread specifics

* VTs **support** `ThreadLocal` and `InheritableThreadLocal` (JEP 444 kept them for compatibility).
* There is no reuse: a VT runs one task and dies, so caching in a `ThreadLocal` gives zero benefit and costs memory.
* `jdk.internal.misc.CarrierThreadLocal` is JDK-internal. Don't depend on carrier identity.
* Find heavy ThreadLocal users with a heap dump, by grouping `ThreadLocalMap$Entry` by referent class.
