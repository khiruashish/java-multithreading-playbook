# Common Bugs Gallery

<span class="level l2">L2</span> → <span class="level l4">L4</span>

Each entry shows the bug, the symptom, and the fix.

### 1. Check-then-act on a concurrent map
```java
if (!map.containsKey(k)) map.put(k, create());       // ❌ two creates
map.computeIfAbsent(k, key -> create());             // ✅
```

### 2. Non-volatile stop flag
`boolean running` read in a loop → the loop never ends after the JIT hoists the read. ✅ Use `volatile`, or interruption.

### 3. `SimpleDateFormat` shared across threads
Garbled dates and `NumberFormatException`. ✅ Use `DateTimeFormatter`.

### 4. `HashMap` modified by several threads
Lost entries, and an infinite loop on Java ≤ 7 during resize. ✅ Use `ConcurrentHashMap`.

### 5. Swallowed `InterruptedException`
```java
catch (InterruptedException e) { }                                // ❌ cancellation lost
catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }  // ✅
```

### 6. Exceptions lost in `submit()`
`executor.submit(task)` with a throwing task, and nobody calls `get()` → silent failure. ✅ Call `get()`, use `execute()` with an
uncaught exception handler, or use structured scopes.

### 7. Periodic task dies silently
`scheduleAtFixedRate` stops forever after one exception. ✅ Wrap the body in `try/catch (Throwable)`.

### 8. Unbounded queue in `newFixedThreadPool`
OOM under load. ✅ Use a bounded `ArrayBlockingQueue` + a rejection policy, or VTs + semaphores.

### 9. Nested `submit().get()` on the same pool
Pool deadlock. ✅ Use separate pools, or VTs.

### 10. Blocking IO in `parallelStream()`
Starves the common pool JVM-wide. ✅ Use a VT executor.

### 11. Blocking inside `ConcurrentHashMap.compute`
Freezes other keys in the bin, and a recursive update throws `IllegalStateException`. ✅ Compute the value outside, then `putIfAbsent`.

### 12. `this` escaping the constructor
```java
public Listener(EventBus bus) { bus.register(this); this.state = init(); } // ❌ events see null state
```
✅ Use a factory method that registers after construction.

### 13. Lock held across a network call
Every thread blocks behind one slow call, and VTs get pinned on JDK 21–23. ✅ Narrow the critical section, and do IO outside it.

### 14. Pooling virtual threads / ThreadLocal caches on VTs
See [L7 pitfalls](../virtual-threads/07-pitfalls.md).

### 15. Missing timeouts with virtual threads
A downstream hangs → hundreds of thousands of parked VTs, heap growth, then OOM. ✅ Put a timeout on every IO call, and deadlines on fan-out.

### 16. `@Transactional` + forking work to another thread
The forked work runs **outside** the transaction (the connection is thread-bound). ✅ Fan out reads before the transaction, and write sequentially.

### 17. Double-checked locking without `volatile`
Another thread can see a partially constructed object. ✅ Use `volatile`, the holder idiom, or `StableValue`.
