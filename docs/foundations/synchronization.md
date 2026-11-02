# `synchronized`, `wait` / `notify`

<span class="level l1">L1</span> <span class="level l2">L2</span>

## Intrinsic locks (monitors)

Every object has a monitor. `synchronized` gives you:

* **Mutual exclusion**: only one thread at a time is inside the block for a given monitor.
* **Visibility**: releasing the monitor *happens-before* the next acquisition.
* **Reentrancy**: the owning thread can re-enter the block.

```java
public class BankAccount {
    private long balancePaise;                     // guarded by this

    public synchronized void deposit(long paise) { balancePaise += paise; }

    public void transfer(BankAccount to, long paise) {
        // lock ordering prevents deadlock: always lock the lower id first
        BankAccount first  = System.identityHashCode(this) < System.identityHashCode(to) ? this : to;
        BankAccount second = first == this ? to : this;
        synchronized (first) {
            synchronized (second) {
                if (balancePaise < paise) throw new IllegalStateException("insufficient funds");
                balancePaise -= paise;
                to.balancePaise += paise;
            }
        }
    }
}
```

!!! warning "Lock on a private final object"
    `synchronized(this)` and `synchronized` methods expose your lock to any caller. In libraries, use
    `private final Object lock = new Object();`. **Never** lock on `String` literals, boxed `Integer`s, or
    value-based classes. They are shared or interned, so unrelated code can end up holding your lock.

## How HotSpot implements it (L3)

| State | When | Cost |
|---|---|---|
| ~~Biased~~ | Removed in JDK 18 (JEP 374) | – |
| Thin / lightweight | Uncontended: CAS on the object header (JDK 21+ has a new "lightweight locking" mode) | ~20 ns |
| Inflated (fat) monitor | Contended or `wait()` called: an `ObjectMonitor` with an entry list | Parks threads in the OS |

**Virtual threads and `synchronized`:**

* **JDK 21–23:** a VT that blocks *inside* `synchronized` (on IO, `sleep`, `wait`, or contended monitor entry) **pins**
  its carrier. The carrier OS thread is blocked too.
* **JDK 24+ (JEP 491):** monitors are owned by the virtual thread, not the carrier. The VT unmounts when it blocks in
  `synchronized` or in `Object.wait()`. Pinning remains only for native frames (JNI, `Unsafe` upcalls) and class
  initializers. See [Pinning](../virtual-threads/03-pinning.md).

## `wait` / `notify`: guarded blocks

```java
public class BoundedBuffer<T> {
    private final Object[] items;
    private int head, tail, count;

    public BoundedBuffer(int capacity) { items = new Object[capacity]; }

    public synchronized void put(T item) throws InterruptedException {
        while (count == items.length) wait();     // ALWAYS a while loop (spurious wakeups)
        items[tail] = item; tail = (tail + 1) % items.length; count++;
        notifyAll();                              // notifyAll: producers and consumers share one wait set
    }

    @SuppressWarnings("unchecked")
    public synchronized T take() throws InterruptedException {
        while (count == 0) wait();
        T item = (T) items[head]; items[head] = null; head = (head + 1) % items.length; count--;
        notifyAll();
        return item;
    }
}
```

Rules:

1. Call `wait/notify` only while holding the monitor. Otherwise you get `IllegalMonitorStateException`.
2. Always wait in a `while` loop that re-checks the condition.
3. Prefer `notifyAll()` unless you can prove every waiter waits for the same condition.
4. In new code, prefer `BlockingQueue` or `Lock` + `Condition` ([Locks](locks.md)), which gives separate wait sets.

## Old vs new: a thread-safe counter

=== "Java 8"

    ```java
    class Stats {
        private long hits;
        synchronized void hit() { hits++; }
        synchronized long hits() { return hits; }
    }
    ```

=== "Java 8+ (better under contention)"

    ```java
    class Stats {
        private final LongAdder hits = new LongAdder();
        void hit() { hits.increment(); }      // striped cells, no lock
        long hits() { return hits.sum(); }
    }
    ```
