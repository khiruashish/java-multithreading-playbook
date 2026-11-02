package com.playbook.jcstress;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/** Can a reader see a published object whose constructor-written field is still 0? */
public class UnsafePublicationTest {

    static class Holder {
        int value;                                     // NOT final
        Holder() { value = 42; }
    }

    static class FinalHolder {
        final int value;                               // final-field semantics (JLS 17.5)
        FinalHolder() { value = 42; }
    }

    @JCStressTest
    @Outcome(id = "-1", expect = ACCEPTABLE, desc = "not published yet")
    @Outcome(id = "42", expect = ACCEPTABLE, desc = "fully initialized")
    @Outcome(id = "0", expect = ACCEPTABLE_INTERESTING, desc = "saw the object but NOT its field: unsafe publication")
    @State
    public static class PlainField {
        Holder h;                                      // plain field: racy publication
        @Actor public void writer() { h = new Holder(); }
        @Actor public void reader(I_Result r) { Holder l = h; r.r1 = (l == null) ? -1 : l.value; }
    }

    @JCStressTest
    @Outcome(id = "-1", expect = ACCEPTABLE, desc = "not published yet")
    @Outcome(id = "42", expect = ACCEPTABLE, desc = "fully initialized")
    @Outcome(id = "0", expect = FORBIDDEN, desc = "final fields are always visible once the reference is")
    @State
    public static class FinalField {
        FinalHolder h;
        @Actor public void writer() { h = new FinalHolder(); }
        @Actor public void reader(I_Result r) { FinalHolder l = h; r.r1 = (l == null) ? -1 : l.value; }
    }
}
