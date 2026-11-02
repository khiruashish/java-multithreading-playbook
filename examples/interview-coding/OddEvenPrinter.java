import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Two threads print 1..N alternately: "odd" prints odd numbers, "even" prints even numbers.
 * Run: java OddEvenPrinter.java
 */
public class OddEvenPrinter {

    // --8<-- [start:monitor]
    /** Approach 1: one monitor + wait/notifyAll. A shared counter decides whose turn it is. */
    static final class MonitorPrinter {
        private final int max;
        private int next = 1;                                   // guarded by "this"

        MonitorPrinter(int max) { this.max = max; }

        void print(boolean odd, List<String> out) throws InterruptedException {
            synchronized (this) {
                while (true) {
                    while (next <= max && isOdd(next) != odd) {
                        wait();                                 // not my turn: release monitor and sleep
                    }
                    if (next > max) {                           // finished: wake the other thread so it can exit too
                        notifyAll();
                        return;
                    }
                    out.add(Thread.currentThread().getName() + ":" + next);
                    next++;                                     // hand the turn over...
                    notifyAll();                                // ...and wake the other thread
                }
            }
        }

        private static boolean isOdd(int n) { return n % 2 == 1; }
    }
    // --8<-- [end:monitor]

    // --8<-- [start:semaphore]
    /** Approach 2: two semaphores used as a baton. Exactly one of them has a permit at any time. */
    static final class SemaphorePrinter {
        private final Semaphore oddTurn = new Semaphore(1);    // odd goes first
        private final Semaphore evenTurn = new Semaphore(0);

        void odd(int max, List<String> out) throws InterruptedException {
            for (int i = 1; i <= max; i += 2) {
                oddTurn.acquire();                             // wait for the baton
                out.add("odd:" + i);
                evenTurn.release();                            // pass the baton
            }
        }

        void even(int max, List<String> out) throws InterruptedException {
            for (int i = 2; i <= max; i += 2) {
                evenTurn.acquire();
                out.add("even:" + i);
                oddTurn.release();
            }
        }
    }
    // --8<-- [end:semaphore]

    // --8<-- [start:main]
    public static void main(String[] args) throws Exception {
        int max = 10;

        List<String> out1 = new ArrayList<>();                 // only touched while holding the monitor
        var p = new MonitorPrinter(max);
        Thread odd = Thread.ofPlatform().name("odd").start(() -> run(() -> p.print(true, out1)));
        Thread even = Thread.ofPlatform().name("even").start(() -> run(() -> p.print(false, out1)));
        odd.join();
        even.join();                                           // join() = happens-before: safe to read out1
        System.out.println("monitor  : " + out1);
        check(out1, max);

        List<String> out2 = new ArrayList<>();                 // release()->acquire() gives happens-before
        var s = new SemaphorePrinter();
        Thread o = Thread.ofVirtual().start(() -> run(() -> s.odd(max, out2)));
        Thread e = Thread.ofVirtual().start(() -> run(() -> s.even(max, out2)));
        o.join();
        e.join();
        System.out.println("semaphore: " + out2);
        check(out2, max);
        System.out.println("PASS");
    }
    // --8<-- [end:main]

    interface Body { void run() throws InterruptedException; }

    static void run(Body b) {
        try { b.run(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void check(List<String> out, int max) {
        if (out.size() != max) throw new AssertionError("size " + out.size());
        for (int i = 0; i < max; i++) {
            if (!out.get(i).endsWith(":" + (i + 1))) throw new AssertionError("wrong order at " + i + ": " + out);
        }
    }
}
