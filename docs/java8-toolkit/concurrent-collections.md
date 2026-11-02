# Concurrent Collections

<span class="level l2">L2</span>

| Need | Class | Notes |
|---|---|---|
| Hash map | `ConcurrentHashMap` | Lock-free reads, per-bin locking on write, atomic `compute*`/`merge` |
| Sorted map/set | `ConcurrentSkipListMap/Set` | Lock-free, O(log n) |
| Read-mostly list/set | `CopyOnWriteArrayList/Set` | Every write copies the array. Listener lists, configs |
| Bounded FIFO handoff | `ArrayBlockingQueue` | One lock, fixed array, predictable memory |
| Optionally bounded FIFO | `LinkedBlockingQueue` | Two locks (put/take), higher throughput. **Always pass a capacity** |
| Direct handoff | `SynchronousQueue` | Capacity 0. Producer waits for a consumer |
| Handoff or queue | `LinkedTransferQueue` (7) | `transfer()` waits for a consumer |
| Priority | `PriorityBlockingQueue` | Unbounded |
| Delayed | `DelayQueue` | Elements become available after a delay |
| Deque | `LinkedBlockingDeque`, `ConcurrentLinkedDeque` | Work stealing, LIFO |
| Non-blocking queue | `ConcurrentLinkedQueue` | Michael-Scott lock-free queue |

## ConcurrentHashMap: atomic compound operations

=== "Wrong (race between check and act)"

    ```java
    if (!map.containsKey(k)) map.put(k, new Session(k));   // two threads can both put
    Integer old = counts.get(word); counts.put(word, old == null ? 1 : old + 1); // lost updates
    ```

=== "Right (Java 8)"

    ```java
    Session s = map.computeIfAbsent(k, Session::new);       // atomic per key
    counts.merge(word, 1, Integer::sum);                    // atomic increment
    // Even better for hot counters:
    ConcurrentHashMap<String, LongAdder> freq = new ConcurrentHashMap<>();
    freq.computeIfAbsent(word, w -> new LongAdder()).increment();
    ```

!!! danger "Never block or call the same map inside `compute*` functions"
    The function runs while holding the bin lock. Calling IO there blocks every other key in the bin. Calling
    `map.computeIfAbsent` recursively on the same map throws `IllegalStateException` ("Recursive update").

Bulk operations (Java 8): `map.forEach(parallelismThreshold, ...)`, `reduceValues`, `search`. These run on the
common pool when the map size exceeds the threshold.

## Iteration semantics

* `ConcurrentHashMap`, `ConcurrentSkipListMap`: **weakly consistent** iterators. They never throw
  `ConcurrentModificationException` and may or may not reflect concurrent updates.
* `CopyOnWriteArrayList`: **snapshot** iterators that see the array as it was when iteration started.
* `Collections.synchronizedList`: you must hold the list's lock while iterating, or you risk a `ConcurrentModificationException`.

## Producer–consumer with virtual threads

```java
BlockingQueue<Order> queue = new ArrayBlockingQueue<>(10_000);       // bounded = backpressure

try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
    ex.submit(() -> { for (Order o : source) queue.put(o); queue.put(Order.POISON); return null; });
    for (int i = 0; i < 100; i++) {                                   // 100 cheap consumers
        ex.submit(() -> {
            for (Order o; (o = queue.take()) != Order.POISON; ) process(o);
            queue.put(Order.POISON);                                  // pass the poison pill on
            return null;
        });
    }
}
```

Blocking on `put`/`take` is free with virtual threads. The VT simply unmounts.
