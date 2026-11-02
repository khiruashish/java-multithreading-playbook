# Benchmarking: JMH & jcstress

<span class="level l3">L3</span> <span class="level l4">L4</span>

## Why `System.nanoTime()` loops lie

JIT warm-up, dead-code elimination, on-stack replacement, and GC all distort hand-rolled timings. The examples in
this repo are good for **orders of magnitude**. Use **JMH** for decisions.

## JMH: platform pool vs virtual threads

```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(value = 2, jvmArgsAppend = {"-XX:+UseZGC"})
public class FanOutBenchmark {

    @Param({"100", "1000", "10000"})
    int concurrency;

    ExecutorService platform;

    @Setup public void setup() { platform = Executors.newFixedThreadPool(200); }
    @TearDown public void tearDown() { platform.close(); }

    static int io() throws InterruptedException { Thread.sleep(10); return 1; }

    @Benchmark
    public int platformPool() throws Exception { return run(platform); }

    @Benchmark
    public int virtualThreads() throws Exception {
        try (var vt = Executors.newVirtualThreadPerTaskExecutor()) { return run(vt); }
    }

    private int run(ExecutorService ex) throws Exception {
        List<Future<Integer>> fs = new ArrayList<>(concurrency);
        for (int i = 0; i < concurrency; i++) fs.add(ex.submit(FanOutBenchmark::io));
        int sum = 0; for (Future<Integer> f : fs) sum += f.get();
        return sum;                          // return the result → no dead-code elimination
    }
}
```

```bash
mvn archetype:generate -DarchetypeGroupId=org.openjdk.jmh -DarchetypeArtifactId=jmh-java-benchmark-archetype
mvn package && java -jar target/benchmarks.jar FanOutBenchmark -prof gc
```

Read `-prof gc` too. VTs allocate stack chunks, so check `gc.alloc.rate.norm` per operation.

## jcstress: testing the memory model

```java
@JCStressTest
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE, desc = "both see each other")
@Outcome(id = {"0, 1", "1, 0"}, expect = Expect.ACCEPTABLE, desc = "interleaved")
@Outcome(id = "0, 0", expect = Expect.ACCEPTABLE_INTERESTING, desc = "store buffering: reordering!")
@State
public class StoreBufferingTest {
    int x, y;                                    // make these volatile and 0,0 disappears
    @Actor public void actor1(II_Result r) { x = 1; r.r1 = y; }
    @Actor public void actor2(II_Result r) { y = 1; r.r2 = x; }
}
```

On x86 this observes `0, 0` (store-buffer reordering) millions of times per run. That's proof that plain fields are
not sequentially consistent.

## Load testing a service

| Tool | Strength |
|---|---|
| **k6** | JS scripts, thresholds in CI, good for HTTP |
| **Gatling** | Scala/Java DSL, rich reports |
| **wrk2 / hey** | Quick, constant-rate (wrk2 avoids coordinated omission) |
| **JFR + async-profiler** | Where the time actually goes under load |

Always compare **at the same request rate**: p50/p99, error rate, CPU, heap, DB pool waiters. Then find the
**max sustainable rate** where p99 meets the SLO.
