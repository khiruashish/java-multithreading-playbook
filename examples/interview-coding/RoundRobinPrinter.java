import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * N threads print 1..max in strict round-robin order: T0 prints 1, T1 prints 2, ... T(N-1) prints N, T0 prints N+1 ...
 * Run: java RoundRobinPrinter.java
 */
public class RoundRobinPrinter {

    // --8<-- [start:core]
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition[] turn;                  // one wait-set per thread: we wake exactly the next one
    private final int threads;
    private final int max;
    private int next = 1;                            // guarded by lock

    RoundRobinPrinter(int threads, int max) {
        this.threads = threads;
        this.max = max;
        this.turn = new Condition[threads];
        for (int i = 0; i < threads; i++) turn[i] = lock.newCondition();
    }

    private int owner(int number) { return (number - 1) % threads; }

    void worker(int id, List<String> out) throws InterruptedException {
        lock.lock();
        try {
            while (true) {
                while (next <= max && owner(next) != id) {
                    turn[id].await();                // releases the lock while waiting
                }
                if (next > max) {                    // done: wake everybody so they observe next > max and exit
                    for (Condition c : turn) c.signalAll();
                    return;
                }
                out.add("T" + id + ":" + next);
                next++;
                turn[owner(next)].signal();          // wake ONLY the thread whose turn it is now
            }
        } finally {
            lock.unlock();
        }
    }
    // --8<-- [end:core]

    public static void main(String[] args) throws Exception {
        int n = 4, max = 20;
        var printer = new RoundRobinPrinter(n, max);
        List<String> out = new ArrayList<>();        // guarded by printer.lock
        List<Thread> ts = new ArrayList<>();
        for (int id = n - 1; id >= 0; id--) {        // start in reverse order on purpose
            int myId = id;
            ts.add(Thread.ofVirtual().name("T" + id).start(() -> {
                try { printer.worker(myId, out); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
        }
        for (Thread t : ts) t.join();
        System.out.println(out);
        for (int i = 0; i < max; i++) {
            String expected = "T" + (i % n) + ":" + (i + 1);
            if (!out.get(i).equals(expected)) throw new AssertionError("expected " + expected + " got " + out.get(i));
        }
        System.out.println("PASS");
    }
}
