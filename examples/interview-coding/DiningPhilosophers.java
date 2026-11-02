import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LeetCode 1226. N philosophers, N forks, each needs both neighbouring forks to eat. Avoid deadlock and starvation.
 * Run: java DiningPhilosophers.java
 */
public class DiningPhilosophers {

    // --8<-- [start:ordering]
    /** Solution 1: resource ordering. Everyone picks up the LOWER-numbered fork first, so no wait-for cycle exists. */
    static final class OrderedTable {
        private final ReentrantLock[] forks;

        OrderedTable(int n) {
            forks = new ReentrantLock[n];
            for (int i = 0; i < n; i++) forks[i] = new ReentrantLock(true);   // fair → no starvation
        }

        void dine(int philosopher, Runnable eat) {
            int left = philosopher;
            int right = (philosopher + 1) % forks.length;
            ReentrantLock first = forks[Math.min(left, right)];
            ReentrantLock second = forks[Math.max(left, right)];
            first.lock();
            try {
                second.lock();
                try { eat.run(); } finally { second.unlock(); }
            } finally {
                first.unlock();
            }
        }
    }
    // --8<-- [end:ordering]

    // --8<-- [start:waiter]
    /** Solution 2: a "waiter" admits at most N-1 philosophers to the table, so at least one can always get both forks. */
    static final class WaiterTable {
        private final ReentrantLock[] forks;
        private final Semaphore seats;

        WaiterTable(int n) {
            forks = new ReentrantLock[n];
            for (int i = 0; i < n; i++) forks[i] = new ReentrantLock();
            seats = new Semaphore(n - 1, true);
        }

        void dine(int philosopher, Runnable eat) throws InterruptedException {
            seats.acquire();
            try {
                ReentrantLock left = forks[philosopher], right = forks[(philosopher + 1) % forks.length];
                left.lock();                      // naive left-then-right is now safe
                try {
                    right.lock();
                    try { eat.run(); } finally { right.unlock(); }
                } finally { left.unlock(); }
            } finally {
                seats.release();
            }
        }
    }
    // --8<-- [end:waiter]

    interface Table { void dine(int p, Runnable eat) throws InterruptedException; }

    static void simulate(String name, Table table, int n, int meals) throws Exception {
        AtomicIntegerArray eaten = new AtomicIntegerArray(n);
        List<Thread> ts = new ArrayList<>();
        for (int p = 0; p < n; p++) {
            int id = p;
            ts.add(Thread.ofVirtual().start(() -> {
                try {
                    for (int m = 0; m < meals; m++) table.dine(id, () -> eaten.incrementAndGet(id));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
        }
        for (Thread t : ts) if (!t.join(Duration.ofSeconds(30))) throw new AssertionError(name + ": deadlock!");
        for (int p = 0; p < n; p++) if (eaten.get(p) != meals) throw new AssertionError(name + " p" + p);
        System.out.printf("%-15s %d philosophers x %,d meals each: all finished, no deadlock%n", name, n, meals);
    }

    public static void main(String[] args) throws Exception {
        var ordered = new OrderedTable(5);
        simulate("resource-order", ordered::dine, 5, 20_000);
        var waiter = new WaiterTable(5);
        simulate("waiter", waiter::dine, 5, 20_000);
        System.out.println("PASS");
    }
}
