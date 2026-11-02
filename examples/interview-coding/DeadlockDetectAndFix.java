import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Create a deadlock on purpose, detect it with ThreadMXBean, then show two fixes (lock ordering and tryLock).
 * Run: java DeadlockDetectAndFix.java
 */
public class DeadlockDetectAndFix {

    // --8<-- [start:create]
    static void createDeadlock(Object a, Object b) {
        CountDownLatch bothHoldFirstLock = new CountDownLatch(2);   // guarantees the bad interleaving
        Thread.ofPlatform().name("worker-A").daemon(true).start(() -> {
            synchronized (a) {
                bothHoldFirstLock.countDown();
                await(bothHoldFirstLock);
                synchronized (b) { System.out.println("never printed"); }
            }
        });
        Thread.ofPlatform().name("worker-B").daemon(true).start(() -> {
            synchronized (b) {                                      // opposite order → cycle
                bothHoldFirstLock.countDown();
                await(bothHoldFirstLock);
                synchronized (a) { System.out.println("never printed"); }
            }
        });
    }
    // --8<-- [end:create]

    // --8<-- [start:detect]
    static long[] detect() throws InterruptedException {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        for (int i = 0; i < 50; i++) {                              // poll up to ~5 s
            long[] ids = mx.findDeadlockedThreads();                // monitors AND j.u.c locks
            if (ids != null) {
                for (ThreadInfo info : mx.getThreadInfo(ids, true, true)) {
                    System.out.printf("  DEADLOCK: %s is waiting for %s held by %s%n",
                            info.getThreadName(), info.getLockName(), info.getLockOwnerName());
                }
                return ids;
            }
            Thread.sleep(100);
        }
        return null;
    }
    // --8<-- [end:detect]

    // --8<-- [start:fix-order]
    /** Fix 1: every thread acquires locks in the same global order (here: by id). No cycle is possible. */
    record Resource(int id, ReentrantLock lock) {}

    static void withBoth(Resource x, Resource y, Runnable action) {
        Resource first = x.id() < y.id() ? x : y;
        Resource second = first == x ? y : x;
        first.lock().lock();
        try {
            second.lock().lock();
            try { action.run(); } finally { second.lock().unlock(); }
        } finally {
            first.lock().unlock();
        }
    }
    // --8<-- [end:fix-order]

    // --8<-- [start:fix-trylock]
    /** Fix 2: never wait for the second lock while holding the first: tryLock with timeout, back off, retry. */
    static boolean withBothTryLock(ReentrantLock x, ReentrantLock y, Runnable action) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (x.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    if (y.tryLock(10, TimeUnit.MILLISECONDS)) {
                        try { action.run(); return true; } finally { y.unlock(); }
                    }
                } finally {
                    x.unlock();                                     // release what we hold → breaks hold-and-wait
                }
            }
            Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 5)); // random backoff vs livelock
        }
        return false;
    }
    // --8<-- [end:fix-trylock]

    public static void main(String[] args) throws Exception {
        System.out.println("1) creating a deadlock...");
        createDeadlock(new Object(), new Object());
        long[] ids = detect();
        if (ids == null || ids.length != 2) throw new AssertionError("deadlock not detected");

        System.out.println("2) lock ordering: 2 threads x 100k opposite-order acquisitions");
        Resource r1 = new Resource(1, new ReentrantLock()), r2 = new Resource(2, new ReentrantLock());
        int[] counter = {0};
        Thread t1 = Thread.ofVirtual().start(() -> { for (int i = 0; i < 100_000; i++) withBoth(r1, r2, () -> counter[0]++); });
        Thread t2 = Thread.ofVirtual().start(() -> { for (int i = 0; i < 100_000; i++) withBoth(r2, r1, () -> counter[0]++); });
        if (!t1.join(java.time.Duration.ofSeconds(20)) || !t2.join(java.time.Duration.ofSeconds(20))) throw new AssertionError("stuck");
        System.out.println("  completed, counter=" + counter[0]);

        System.out.println("3) tryLock + backoff: 2 threads x 10k opposite-order acquisitions");
        ReentrantLock a = new ReentrantLock(), b = new ReentrantLock();
        int[] c2 = {0};
        Thread u1 = Thread.ofVirtual().start(() -> uncheck(() -> { for (int i = 0; i < 10_000; i++) if (!withBothTryLock(a, b, () -> c2[0]++)) throw new AssertionError(); }));
        Thread u2 = Thread.ofVirtual().start(() -> uncheck(() -> { for (int i = 0; i < 10_000; i++) if (!withBothTryLock(b, a, () -> c2[0]++)) throw new AssertionError(); }));
        u1.join(); u2.join();
        System.out.println("  completed, counter=" + c2[0]);
        if (counter[0] != 200_000 || c2[0] != 20_000) throw new AssertionError();
        System.out.println("PASS");                     // the two deadlocked daemon threads die with the JVM
    }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    interface Body { void run() throws InterruptedException; }

    static void uncheck(Body b) {
        try { b.run(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
