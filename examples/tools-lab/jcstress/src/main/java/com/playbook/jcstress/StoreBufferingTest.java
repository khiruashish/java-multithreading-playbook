package com.playbook.jcstress;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/** Dekker-style store buffering. Can both threads miss each other's write? */
public class StoreBufferingTest {

    @JCStressTest
    @Outcome(id = {"0, 1", "1, 0", "1, 1"}, expect = ACCEPTABLE, desc = "some interleaving")
    @Outcome(id = "0, 0", expect = ACCEPTABLE_INTERESTING, desc = "both reads before both writes: REORDERING")
    @State
    public static class Plain {
        int x, y;
        @Actor public void actor1(II_Result r) { x = 1; r.r1 = y; }
        @Actor public void actor2(II_Result r) { y = 1; r.r2 = x; }
    }

    @JCStressTest
    @Outcome(id = {"0, 1", "1, 0", "1, 1"}, expect = ACCEPTABLE, desc = "some interleaving")
    @Outcome(id = "0, 0", expect = FORBIDDEN, desc = "volatile forbids this (sequential consistency)")
    @State
    public static class Volatile {
        volatile int x, y;
        @Actor public void actor1(II_Result r) { x = 1; r.r1 = y; }
        @Actor public void actor2(II_Result r) { y = 1; r.r2 = x; }
    }
}
