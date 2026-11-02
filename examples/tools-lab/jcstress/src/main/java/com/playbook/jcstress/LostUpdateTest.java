package com.playbook.jcstress;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

import java.util.concurrent.atomic.AtomicInteger;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/** Two threads each do one increment. The @Arbiter runs after both and records the final value. */
public class LostUpdateTest {

    @JCStressTest
    @Outcome(id = "2", expect = ACCEPTABLE, desc = "both increments counted")
    @Outcome(id = "1", expect = ACCEPTABLE_INTERESTING, desc = "LOST UPDATE: count++ is read-modify-write")
    @State
    public static class VolatilePlusPlus {
        volatile int count;                       // volatile does NOT make ++ atomic
        @Actor public void actor1() { count++; }
        @Actor public void actor2() { count++; }
        @Arbiter public void arbiter(I_Result r) { r.r1 = count; }
    }

    @JCStressTest
    @Outcome(id = "2", expect = ACCEPTABLE, desc = "both increments counted")
    @Outcome(id = "1", expect = FORBIDDEN, desc = "impossible with CAS")
    @State
    public static class Atomic {
        final AtomicInteger count = new AtomicInteger();
        @Actor public void actor1() { count.incrementAndGet(); }
        @Actor public void actor2() { count.incrementAndGet(); }
        @Arbiter public void arbiter(I_Result r) { r.r1 = count.get(); }
    }
}
