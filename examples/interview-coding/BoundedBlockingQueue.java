import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LeetCode 1188 / classic: implement a bounded blocking queue with put/take (+ timed offer) from scratch.
 * Run: java BoundedBlockingQueue.java
 */
public class BoundedBlockingQueue<E> {

    // --8<-- [start:fields]
    private final Object[] items;                 // circular buffer
    private int head;                             // next slot to take from
    private int tail;                             // next slot to put into
    private int count;                            // number of elements
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();   // producers wait here
    private final Condition notEmpty = lock.newCondition();  // consumers wait here

    public BoundedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        items = new Object[capacity];
    }
    // --8<-- [end:fields]

    // --8<-- [start:put-take]
    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        lock.lockInterruptibly();                 // a blocked producer can still be cancelled
        try {
            while (count == items.length) {
                notFull.await();                  // queue full → wait for a consumer
            }
            enqueue(e);
            notEmpty.signal();                    // exactly one consumer can now make progress
        } finally {
            lock.unlock();
        }
    }

    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                notEmpty.await();                 // queue empty → wait for a producer
            }
            E e = dequeue();
            notFull.signal();                     // exactly one producer can now make progress
            return e;
        } finally {
            lock.unlock();
        }
    }
    // --8<-- [end:put-take]

    // --8<-- [start:offer]
    /** Timed offer: gives up after the timeout. awaitNanos returns the time still left. */
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (count == items.length) {
                if (nanos <= 0) return false;
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(e);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }
    // --8<-- [end:offer]

    // --8<-- [start:helpers]
    private void enqueue(E e) {                   // caller holds lock
        items[tail] = e;
        tail = (tail + 1) % items.length;
        count++;
    }

    @SuppressWarnings("unchecked")
    private E dequeue() {                         // caller holds lock
        E e = (E) items[head];
        items[head] = null;                       // let GC reclaim it
        head = (head + 1) % items.length;
        count--;
        return e;
    }

    public int size() {
        lock.lock();
        try { return count; } finally { lock.unlock(); }
    }
    // --8<-- [end:helpers]

    public static void main(String[] args) throws Exception {
        var q = new BoundedBlockingQueue<Integer>(8);              // tiny capacity → lots of blocking
        int producers = 20, perProducer = 5_000;
        LongAdder sum = new LongAdder();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int p = 0; p < producers; p++) {
                ex.submit(() -> { for (int i = 1; i <= perProducer; i++) q.put(i); return null; });
            }
            for (int c = 0; c < 10; c++) {
                ex.submit(() -> { for (int i = 0; i < producers * perProducer / 10; i++) sum.add(q.take()); return null; });
            }
        }
        long expected = (long) producers * perProducer * (perProducer + 1) / 2;
        if (sum.sum() != expected || q.size() != 0) throw new AssertionError(sum.sum() + " vs " + expected);
        boolean accepted = new BoundedBlockingQueue<Integer>(1) {{ put(1); }}.offer(2, 50, TimeUnit.MILLISECONDS);
        if (accepted) throw new AssertionError("offer on a full queue must time out");
        System.out.printf("20 producers x 5000 items through capacity 8: sum=%d (expected %d), timed offer on full queue=%s%n",
                sum.sum(), expected, accepted);
        System.out.println("PASS");
    }
}
