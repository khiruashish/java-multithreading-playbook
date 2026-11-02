import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implement a reusable CyclicBarrier (with a barrier action) from scratch. The key insight: a GENERATION object,
 * so threads from the next round can't be confused with threads still leaving the current round.
 * Run: java CustomCyclicBarrier.java
 */
public class CustomCyclicBarrier {

    // --8<-- [start:core]
    private static final class Generation {}           // identity object: one per "round"

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition tripped = lock.newCondition();
    private final int parties;
    private final Runnable barrierAction;
    private int waiting;                               // guarded by lock
    private Generation generation = new Generation();  // guarded by lock

    public CustomCyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.barrierAction = barrierAction;
    }

    /** @return arrival index: parties-1 for the first to arrive, 0 for the last (same as the JDK) */
    public int await() throws InterruptedException {
        lock.lock();
        try {
            Generation myGeneration = generation;
            int index = parties - 1 - waiting;
            waiting++;
            if (waiting == parties) {                  // last to arrive: trip the barrier
                if (barrierAction != null) barrierAction.run();   // runs before anyone is released
                waiting = 0;
                generation = new Generation();         // start the next round
                tripped.signalAll();
                return 0;
            }
            while (myGeneration == generation) {       // NOT "while waiting < parties": waiting was reset to 0!
                tripped.await();
            }
            return index;
        } finally {
            lock.unlock();
        }
    }
    // --8<-- [end:core]

    public static void main(String[] args) throws Exception {
        int parties = 8, rounds = 1_000;
        AtomicInteger roundsCompleted = new AtomicInteger();
        int[] perRound = new int[rounds];                           // each worker increments its round's slot
        AtomicInteger violations = new AtomicInteger();
        var barrier = new CustomCyclicBarrier(parties, () -> {
            int r = roundsCompleted.get();
            if (perRound[r] != parties) violations.incrementAndGet();   // all parties finished round r?
            roundsCompleted.incrementAndGet();
        });
        Object lock = new Object();
        List<Thread> ts = new ArrayList<>();
        for (int p = 0; p < parties; p++) {
            ts.add(Thread.ofVirtual().start(() -> {
                try {
                    for (int r = 0; r < rounds; r++) {
                        synchronized (lock) { perRound[r]++; }
                        barrier.await();                            // nobody starts round r+1 before all finish r
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
        }
        for (Thread t : ts) t.join();
        System.out.printf("%d parties x %d rounds: rounds completed=%d, ordering violations=%d%n",
                parties, rounds, roundsCompleted.get(), violations.get());
        if (roundsCompleted.get() != rounds || violations.get() != 0) throw new AssertionError();
        System.out.println("PASS");
    }
}
