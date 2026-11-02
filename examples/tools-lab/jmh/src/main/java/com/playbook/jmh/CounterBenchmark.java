package com.playbook.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Four threads increment ONE shared counter as fast as they can. Which counter scales under contention?
 * Run: java -jar target/benchmarks.jar CounterBenchmark
 */
@State(Scope.Group)                                  // one counter instance shared by the 4 threads of a group
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CounterBenchmark {

    private long syncCounter;
    private final Object monitor = new Object();
    private long lockCounter;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong atomic = new AtomicLong();
    private final LongAdder adder = new LongAdder();

    @Benchmark @Group("synchronized") @GroupThreads(4)
    public long synchronizedIncrement() {
        synchronized (monitor) { return ++syncCounter; }
    }

    @Benchmark @Group("reentrantLock") @GroupThreads(4)
    public long reentrantLockIncrement() {
        lock.lock();
        try { return ++lockCounter; } finally { lock.unlock(); }
    }

    @Benchmark @Group("atomicLong") @GroupThreads(4)
    public long atomicIncrement() {
        return atomic.incrementAndGet();               // returned → JMH consumes it, no dead-code elimination
    }

    @Benchmark @Group("longAdder") @GroupThreads(4)
    public void adderIncrement() {
        adder.increment();                              // side effect on shared state is enough
    }
}
