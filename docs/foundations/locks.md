# Explicit Locks

<span class="level l2">L2</span> <span class="level l3">L3</span>

## `ReentrantLock` vs `synchronized`

| Capability | `synchronized` | `ReentrantLock` |
|---|---|---|
| Auto-release | Yes (block scope) | No. You **must** use `try/finally` |
| `tryLock()` / timeout | No | Yes |
| Interruptible acquire | No | `lockInterruptibly()` |
| Fairness | No | `new ReentrantLock(true)` |
| Multiple condition queues | One wait set | Many `Condition`s |
| Lock diagnostics | Thread dump only | `isLocked`, `getQueueLength`, `hasQueuedThreads` |
| VT-friendly (no pinning) | **JDK 24+** | Always (built on `LockSupport.park`) |

```java
private final ReentrantLock lock = new ReentrantLock();

public boolean tryReserve(String sku, int qty) throws InterruptedException {
    if (!lock.tryLock(50, TimeUnit.MILLISECONDS)) return false;  // fail fast instead of piling up
    try {
        return stock.reserve(sku, qty);
    } finally {
        lock.unlock();
    }
}
```

## `Condition`: separate wait sets

```java
public class BoundedQueue<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final ArrayDeque<T> q = new ArrayDeque<>();
    private final int cap;

    public BoundedQueue(int cap) { this.cap = cap; }

    public void put(T t) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (q.size() == cap) notFull.await();
            q.addLast(t);
            notEmpty.signal();               // wake ONE consumer, not every thread
        } finally { lock.unlock(); }
    }

    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (q.isEmpty()) notEmpty.await();
            T t = q.removeFirst();
            notFull.signal();
            return t;
        } finally { lock.unlock(); }
    }
}
```

## `ReentrantReadWriteLock`

Many readers or one writer. Good when reads are long and frequent and writes are rare. Watch for **writer starvation**
in non-fair mode. A thread can downgrade a write lock to a read lock, but it can't upgrade a read lock to a write lock.
Upgrading deadlocks.

## `StampedLock` (Java 8): optimistic reads

```java
public class Point {
    private final StampedLock sl = new StampedLock();
    private double x, y;

    public void move(double dx, double dy) {
        long stamp = sl.writeLock();
        try { x += dx; y += dy; } finally { sl.unlockWrite(stamp); }
    }

    public double distanceFromOrigin() {
        long stamp = sl.tryOptimisticRead();       // no lock taken, just a version stamp
        double cx = x, cy = y;
        if (!sl.validate(stamp)) {                 // a writer intervened: fall back to a real read lock
            stamp = sl.readLock();
            try { cx = x; cy = y; } finally { sl.unlockRead(stamp); }
        }
        return Math.hypot(cx, cy);
    }
}
```

!!! danger "StampedLock is NOT reentrant and has no Conditions"
    Re-acquiring it from the same thread deadlocks. Use it for small, leaf-level, read-mostly state.

## Which lock? (decision table)

| Situation | Choose |
|---|---|
| Simple critical section, JDK 24+ | `synchronized` |
| Simple critical section with blocking IO inside, JDK 21–23 on VTs | `ReentrantLock` |
| Need timeout, `tryLock`, or interruptible acquire | `ReentrantLock` |
| Multiple wait conditions | `ReentrantLock` + `Condition` |
| Read-heavy, tiny critical section | `StampedLock` (optimistic) |
| Read-heavy map | `ConcurrentHashMap` (no lock at all) |
| Limit N concurrent holders | `Semaphore` |
| Cross-JVM exclusion | DB row lock, Redis/ZooKeeper lease ([Distributed concurrency](../system-design/distributed-concurrency.md)) |
