import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implement a read-write lock: many concurrent readers OR one writer. Writer-preferring to avoid writer starvation.
 * Run: java SimpleReadWriteLock.java
 */
public class SimpleReadWriteLock {

    // --8<-- [start:core]
    private int activeReaders;          // readers currently holding the lock
    private int waitingWriters;         // writers queued: new readers must back off (writer preference)
    private boolean writerActive;       // a writer holds the lock

    public synchronized void lockRead() throws InterruptedException {
        while (writerActive || waitingWriters > 0) {
            wait();
        }
        activeReaders++;
    }

    public synchronized void unlockRead() {
        if (activeReaders <= 0) throw new IllegalMonitorStateException("no read lock held");
        activeReaders--;
        if (activeReaders == 0) notifyAll();          // last reader out lets a writer in
    }

    public synchronized void lockWrite() throws InterruptedException {
        waitingWriters++;
        try {
            while (writerActive || activeReaders > 0) {
                wait();
            }
        } finally {
            waitingWriters--;                          // also on interrupt, or readers would block forever
        }
        writerActive = true;
    }

    public synchronized void unlockWrite() {
        if (!writerActive) throw new IllegalMonitorStateException("no write lock held");
        writerActive = false;
        notifyAll();                                   // wake both readers and writers
    }
    // --8<-- [end:core]

    public static void main(String[] args) throws Exception {
        var rw = new SimpleReadWriteLock();
        AtomicInteger readersInside = new AtomicInteger(), writersInside = new AtomicInteger();
        AtomicInteger maxReaders = new AtomicInteger(), violations = new AtomicInteger();
        long[] counter = {0};                          // shared data protected by rw

        // Platform threads on purpose: this lock is built on Object.wait(). On JDK 21-23 a virtual thread in
        // Object.wait() PINS its carrier; 2,000 waiting VTs exhaust the carrier pool (max 256) and the program
        // hangs. JDK 24+ (JEP 491) fixes that; on 21 use a Lock/Condition-based design with VTs instead.
        try (var ex = Executors.newFixedThreadPool(64)) {
            for (int i = 0; i < 2_000; i++) {
                boolean writer = i % 10 == 0;
                ex.submit(() -> {
                    if (writer) {
                        rw.lockWrite();
                        try {
                            if (writersInside.incrementAndGet() != 1 || readersInside.get() != 0) violations.incrementAndGet();
                            counter[0]++;
                            Thread.sleep(1);
                            writersInside.decrementAndGet();
                        } finally { rw.unlockWrite(); }
                    } else {
                        rw.lockRead();
                        try {
                            int now = readersInside.incrementAndGet();
                            maxReaders.accumulateAndGet(now, Math::max);
                            if (writersInside.get() != 0) violations.incrementAndGet();
                            Thread.sleep(1);
                            readersInside.decrementAndGet();
                        } finally { rw.unlockRead(); }
                    }
                    return null;
                });
            }
        }
        System.out.printf("writes=%d violations=%d maxConcurrentReaders=%d%n", counter[0], violations.get(), maxReaders.get());
        if (violations.get() != 0 || counter[0] != 200 || maxReaders.get() < 2) throw new AssertionError();
        System.out.println("PASS");
    }
}
