package com.playbook.tools;

import org.jetbrains.kotlinx.lincheck.LinChecker;
import org.jetbrains.kotlinx.lincheck.annotations.Operation;
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions;
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Lincheck generates concurrent scenarios from the @Operation methods, runs them, and checks every result against
 * some SEQUENTIAL execution (linearizability). Model checking explores interleavings systematically and prints the
 * exact interleaving that breaks your code.
 */
class LincheckTest {

    /** Lincheck test for the BROKEN counter: we EXPECT it to find a bug. */
    public static class BrokenCounterTest {
        private final BrokenCounter counter = new BrokenCounter();
        @Operation public int incrementAndGet() { return counter.incrementAndGet(); }
        @Operation public int get() { return counter.get(); }
    }

    /** Same operations on a correct implementation. */
    public static class AtomicCounterTest {
        private final AtomicInteger counter = new AtomicInteger();
        @Operation public int incrementAndGet() { return counter.incrementAndGet(); }
        @Operation public int get() { return counter.get(); }
    }

    @Test
    void modelCheckingFindsTheLostUpdate() {
        Throwable failure = assertThrows(Throwable.class,
                () -> LinChecker.check(BrokenCounterTest.class, new ModelCheckingOptions()));
        System.out.println("=== Lincheck report for BrokenCounter ===\n" + failure.getMessage());
    }

    @Test
    void atomicCounterIsLinearizable() {
        LinChecker.check(AtomicCounterTest.class, new ModelCheckingOptions());
        LinChecker.check(AtomicCounterTest.class, new StressOptions());
    }
}
