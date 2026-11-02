# Java 8 Toolkit

<span class="level l2">L2</span>

`java.util.concurrent` (Java 5) plus the Java 8 additions are still the backbone of every Java service. Virtual threads
**don't replace** this toolkit. They change *which pieces you use for what*:

| Tool | Role before Java 21 | Role with virtual threads |
|---|---|---|
| `ThreadPoolExecutor` | Run all IO and CPU work | CPU-bound work only, plus legacy code |
| `CompletableFuture` | Avoid blocking threads during IO | Still useful for composition. Plain blocking code is often simpler |
| `ForkJoinPool` | Divide-and-conquer CPU work | Same. Also the default VT scheduler |
| Concurrent collections | Shared state | Same, and even more important with millions of threads |
| Synchronizers | Coordination | Same. `Semaphore` becomes the main way to limit concurrency |
| `ThreadLocal` | Per-thread cache and context | Context only, ideally replaced by `ScopedValue` |
