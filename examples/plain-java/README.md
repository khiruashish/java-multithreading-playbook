# Plain-Java examples

Single-file programs; run with `java <File>.java` on JDK 21+ (no build tool needed).

| File | Command | What it shows |
|---|---|---|
| `MillionThreads.java` | `java MillionThreads.java [count]` | 1M virtual threads each sleeping 1 s |
| `PlatformVsVirtualBenchmark.java` | `java PlatformVsVirtualBenchmark.java` | Fixed pools vs VT-per-task for IO-bound work |
| `FanOutComparison.java` | `java FanOutComparison.java` | Java 8 CompletableFuture fan-out vs Java 21 blocking fan-out under load |
| `PinningDemo.java` | `java -Djdk.virtualThreadScheduler.parallelism=2 PinningDemo.java` | `synchronized` pinning on JDK 21–23 vs `ReentrantLock`; equal on 24+ |
| `RaceConditionDemo.java` | `java RaceConditionDemo.java` | Lost updates and three fixes |
| `StructuredConcurrencyDemo.java` | `java --enable-preview --source 21 StructuredConcurrencyDemo.java` | JDK 21–24 preview API: fail-fast + sibling cancellation |
| `ScopedValueDemo.java` | `java --enable-preview --source 21 ScopedValueDemo.java` | Scoped value binding, inheritance into subtasks, rebinding |

The structured-concurrency and scoped-value demos use the **JDK 21–24** preview API. On JDK 25 scoped values are final and
`StructuredTaskScope` uses `open(Joiner)` — see `docs/modern/structured-concurrency.md`.
