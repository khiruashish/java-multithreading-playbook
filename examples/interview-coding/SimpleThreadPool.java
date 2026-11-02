import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Build a fixed-size thread pool: bounded queue, Future results, rejection, graceful shutdown.
 * Run: java SimpleThreadPool.java
 */
public class SimpleThreadPool implements AutoCloseable {

    // --8<-- [start:fields]
    private final BlockingQueue<Runnable> queue;
    private final List<Thread> workers = new ArrayList<>();
    private final Object stateLock = new Object();    // makes "check shutdown + enqueue" atomic
    private volatile boolean shutdown;

    public SimpleThreadPool(int threads, int queueCapacity) {
        queue = new ArrayBlockingQueue<>(queueCapacity);
        for (int i = 0; i < threads; i++) {
            workers.add(Thread.ofPlatform().name("pool-worker-", i).start(this::workerLoop));
        }
    }
    // --8<-- [end:fields]

    // --8<-- [start:worker]
    private void workerLoop() {
        while (true) {
            Runnable task;
            try {
                task = queue.poll(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return;                                // shutdownNow(): stop immediately
            }
            if (task == null) {
                if (shutdown && queue.isEmpty()) return;  // drained and no more submissions → exit
                continue;
            }
            try {
                task.run();                            // FutureTask captures exceptions into the Future
            } catch (Throwable t) {
                // a bare Runnable threw: keep the worker alive (a real pool would log this)
            }
        }
    }
    // --8<-- [end:worker]

    // --8<-- [start:submit]
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<>(callable);
        synchronized (stateLock) {                     // see "the shutdown race" in the docs
            if (shutdown) throw new RejectedExecutionException("pool is shut down");
            if (!queue.offer(task)) throw new RejectedExecutionException("queue full");  // AbortPolicy
        }
        return task;
    }

    public void shutdown() {
        synchronized (stateLock) { shutdown = true; }  // after this no task can be enqueued
    }

    public List<Runnable> shutdownNow() {
        shutdown();
        List<Runnable> pending = new ArrayList<>();
        queue.drainTo(pending);
        workers.forEach(Thread::interrupt);
        return pending;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (Thread w : workers) {
            long left = deadline - System.nanoTime();
            if (left <= 0 || !w.join(java.time.Duration.ofNanos(left))) return false;
        }
        return true;
    }

    @Override
    public void close() throws InterruptedException {
        shutdown();
        if (!awaitTermination(30, TimeUnit.SECONDS)) shutdownNow();
    }
    // --8<-- [end:submit]

    public static void main(String[] args) throws Exception {
        AtomicInteger ran = new AtomicInteger();
        List<Future<Integer>> results = new ArrayList<>();
        try (var pool = new SimpleThreadPool(4, 1_000)) {
            for (int i = 0; i < 500; i++) {
                int n = i;
                results.add(pool.submit(() -> { ran.incrementAndGet(); return n * n; }));
            }
            Future<Integer> failing = pool.submit(() -> { throw new IllegalStateException("boom"); });
            try { failing.get(); throw new AssertionError("expected failure"); }
            catch (java.util.concurrent.ExecutionException e) { System.out.println("failure captured in Future: " + e.getCause()); }
        }
        long sum = 0;
        for (Future<Integer> f : results) sum += f.get();
        long expected = 0; for (int i = 0; i < 500; i++) expected += (long) i * i;
        if (sum != expected || ran.get() != 500) throw new AssertionError(sum + " " + ran);

        var tiny = new SimpleThreadPool(1, 1);
        tiny.submit(() -> { Thread.sleep(200); return 0; });
        Thread.sleep(20);                                   // worker picks the first task
        tiny.submit(() -> 1);                               // fills the queue (capacity 1)
        boolean rejected = false;
        try { tiny.submit(() -> 2); } catch (RejectedExecutionException e) { rejected = true; }
        tiny.close();
        boolean rejectedAfterShutdown = false;
        try { tiny.submit(() -> 3); } catch (RejectedExecutionException e) { rejectedAfterShutdown = true; }
        System.out.printf("500 tasks ok (sum of squares=%d), queue-full rejection=%s, post-shutdown rejection=%s%n",
                sum, rejected, rejectedAfterShutdown);
        if (!rejected || !rejectedAfterShutdown) throw new AssertionError();
        System.out.println("PASS");
    }
}
