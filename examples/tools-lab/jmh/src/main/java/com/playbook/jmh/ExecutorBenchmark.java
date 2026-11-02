package com.playbook.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * One "request" = fan out N blocking IO calls (10 ms each) and wait for all. Average time per request.
 * Platform pool of 200 vs a new virtual thread per call.
 * Run: java -jar target/benchmarks.jar ExecutorBenchmark
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class ExecutorBenchmark {

    @Param({"100", "1000"})
    int fanOut;

    ExecutorService platformPool;

    @Setup(Level.Trial)
    public void setUp() { platformPool = Executors.newFixedThreadPool(200); }

    @TearDown(Level.Trial)
    public void tearDown() { platformPool.close(); }

    static int blockingIo() throws InterruptedException {
        Thread.sleep(10);
        return 1;
    }

    @Benchmark
    public int platformPool200() throws Exception {
        return fanOut(platformPool);
    }

    @Benchmark
    public int virtualThreads() throws Exception {
        try (var vt = Executors.newVirtualThreadPerTaskExecutor()) {
            return fanOut(vt);
        }
    }

    private int fanOut(ExecutorService ex) throws Exception {
        List<Future<Integer>> fs = new ArrayList<>(fanOut);
        for (int i = 0; i < fanOut; i++) fs.add(ex.submit(ExecutorBenchmark::blockingIo));
        int sum = 0;
        for (Future<Integer> f : fs) sum += f.get();
        return sum;
    }
}
