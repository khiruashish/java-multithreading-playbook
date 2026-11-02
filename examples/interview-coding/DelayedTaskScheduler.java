import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Build a mini ScheduledExecutorService: schedule(task, delay). One dispatcher thread sleeps exactly until the
 * earliest task is due, and wakes early if an even earlier task is scheduled. Due tasks run on virtual threads.
 * Run: java DelayedTaskScheduler.java
 */
public class DelayedTaskScheduler implements AutoCloseable {

    // --8<-- [start:fields]
    private record Job(long dueNanos, long seq, Runnable task) implements Comparable<Job> {
        public int compareTo(Job o) {
            int c = Long.compare(dueNanos, o.dueNanos);
            return c != 0 ? c : Long.compare(seq, o.seq);      // FIFO for equal due times
        }
    }

    private final PriorityQueue<Job> queue = new PriorityQueue<>();   // min-heap by due time, guarded by lock
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();           // "new head or shutdown"
    private final AtomicLong seq = new AtomicLong();
    private final Thread dispatcher;
    private boolean shutdown;                                        // guarded by lock

    public DelayedTaskScheduler() {
        dispatcher = Thread.ofPlatform().name("scheduler-dispatcher").daemon(true).start(this::dispatchLoop);
    }
    // --8<-- [end:fields]

    // --8<-- [start:schedule]
    public void schedule(Runnable task, long delay, TimeUnit unit) {
        Job job = new Job(System.nanoTime() + unit.toNanos(delay), seq.getAndIncrement(), task);
        lock.lock();
        try {
            if (shutdown) throw new IllegalStateException("scheduler is shut down");
            queue.add(job);
            if (queue.peek() == job) changed.signal();   // new earliest job → dispatcher must re-compute its sleep
        } finally {
            lock.unlock();
        }
    }
    // --8<-- [end:schedule]

    // --8<-- [start:dispatch]
    private void dispatchLoop() {
        while (true) {
            Job due;
            lock.lock();
            try {
                while (true) {
                    if (shutdown) return;
                    Job head = queue.peek();
                    if (head == null) {
                        changed.await();                              // nothing scheduled: sleep until schedule()
                        continue;
                    }
                    long wait = head.dueNanos() - System.nanoTime();
                    if (wait <= 0) {
                        due = queue.poll();                           // it's time
                        break;
                    }
                    changed.awaitNanos(wait);                         // sleep until due, or until a new head arrives
                }
            } catch (InterruptedException e) {
                return;
            } finally {
                lock.unlock();
            }
            Thread.ofVirtual().start(due.task());                     // never run user code on the dispatcher
        }
    }

    @Override
    public void close() {
        lock.lock();
        try { shutdown = true; changed.signal(); } finally { lock.unlock(); }
    }
    // --8<-- [end:dispatch]

    public static void main(String[] args) throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(5);
        long t0 = System.nanoTime();
        try (var s = new DelayedTaskScheduler()) {
            s.schedule(() -> { order.add("C@300"); done.countDown(); }, 300, TimeUnit.MILLISECONDS);
            s.schedule(() -> { order.add("A@100"); done.countDown(); }, 100, TimeUnit.MILLISECONDS);
            s.schedule(() -> { order.add("E@500"); done.countDown(); }, 500, TimeUnit.MILLISECONDS);
            Thread.sleep(50);
            s.schedule(() -> { order.add("B@150"); done.countDown(); }, 100, TimeUnit.MILLISECONDS); // new head mid-sleep
            s.schedule(() -> { order.add("D@450"); done.countDown(); }, 400, TimeUnit.MILLISECONDS);
            if (!done.await(2, TimeUnit.SECONDS)) throw new AssertionError("tasks did not run");
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("execution order: " + order + " finished after " + ms + " ms");
        if (!order.equals(List.of("A@100", "B@150", "C@300", "D@450", "E@500"))) throw new AssertionError();
        System.out.println("PASS");
    }
}
