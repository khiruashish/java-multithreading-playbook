# Coding Problems

Quick-reference versions of classic whiteboard problems. For **21 problems with runnable, tested solutions, diagrams, and
line-by-line explanations**, see the [Coding Interview tab](../coding-interview/index.md).

## 1. Print odd/even numbers alternately with two threads

```java
public class OddEven {
    private final Object lock = new Object();
    private int n = 1;
    private final int max;
    OddEven(int max) { this.max = max; }

    void print(boolean odd) {
        synchronized (lock) {
            while (n <= max) {
                if ((n % 2 == 1) == odd) {
                    System.out.println(Thread.currentThread().getName() + ": " + n++);
                    lock.notifyAll();
                } else {
                    try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            }
            lock.notifyAll();
        }
    }
    public static void main(String[] a) throws InterruptedException {
        var p = new OddEven(10);
        Thread t1 = Thread.ofPlatform().name("odd").start(() -> p.print(true));
        Thread t2 = Thread.ofPlatform().name("even").start(() -> p.print(false));
        t1.join(); t2.join();
    }
}
```
*Follow-up:* generalize to N threads printing in round-robin. Use `n % N == id`, or one `Semaphore` per thread and release the next one.

## 2. Bounded blocking queue

See [`synchronized` + wait/notify](../foundations/synchronization.md#wait-notify-guarded-blocks) and
[`Lock` + `Condition`](../foundations/locks.md#condition-separate-wait-sets).

## 3. Thread-safe singleton (three ways)

Holder idiom, `enum Singleton { INSTANCE }`, or DCL with `volatile`. See [JMM](../foundations/java-memory-model.md#double-checked-locking-then-and-now).

## 4. Implement a simple thread pool

```java
public final class SimplePool implements AutoCloseable {
    private final BlockingQueue<Runnable> queue;
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean shutdown;

    public SimplePool(int threads, int capacity) {
        queue = new ArrayBlockingQueue<>(capacity);
        for (int i = 0; i < threads; i++) {
            workers.add(Thread.ofPlatform().name("pool-", i).start(() -> {
                while (!shutdown || !queue.isEmpty()) {
                    try {
                        Runnable r = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (r != null) r.run();
                    } catch (InterruptedException e) { return; }
                    catch (RuntimeException e) { /* log, keep worker alive */ }
                }
            }));
        }
    }
    public void submit(Runnable r) throws InterruptedException {
        if (shutdown) throw new RejectedExecutionException();
        queue.put(r);                                     // blocks when full = backpressure
    }
    @Override public void close() throws InterruptedException {
        shutdown = true;
        for (Thread w : workers) w.join();
    }
}
```

## 5. Deadlock: write one, then fix it

```java
Object a = new Object(), b = new Object();
Thread.ofPlatform().start(() -> { synchronized (a) { sleep(50); synchronized (b) {} } });
Thread.ofPlatform().start(() -> { synchronized (b) { sleep(50); synchronized (a) {} } });  // deadlock
// Fix: both threads lock a then b (global order), or use tryLock with a timeout
```

## 6. Producer–consumer with a poison pill

See [Concurrent Collections](../java8-toolkit/concurrent-collections.md#producerconsumer-with-virtual-threads).

## 7. Concurrent LRU cache

```java
public final class LruCache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, V> map;
    private final ReentrantLock lock = new ReentrantLock();
    public LruCache(int capacity) {
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {     // access order
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> e) { return size() > LruCache.this.capacity; }
        };
    }
    public V get(K k) { lock.lock(); try { return map.get(k); } finally { lock.unlock(); } }  // get mutates order → needs the lock
    public void put(K k, V v) { lock.lock(); try { map.put(k, v); } finally { lock.unlock(); } }
}
// Production: Caffeine (lock-amortized, W-TinyLFU) beats this by far.
```

## 8. Rate limiter (token bucket)

See [Patterns → token bucket](../virtual-threads/05-patterns.md#pattern-3-rate-limit-token-bucket-vt-friendly).

## 9. Fetch N URLs concurrently with a max of K in flight and an overall timeout

=== "Java 8"
    ```java
    ExecutorService pool = Executors.newFixedThreadPool(K);
    List<Callable<String>> calls = urls.stream().<Callable<String>>map(u -> () -> fetch(u)).collect(toList());
    List<Future<String>> fs = pool.invokeAll(calls, 5, TimeUnit.SECONDS);
    pool.shutdownNow();
    ```
=== "Java 21"
    ```java
    Semaphore permits = new Semaphore(K);
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
        List<Callable<String>> calls = urls.stream().<Callable<String>>map(u -> () -> {
            permits.acquire();
            try { return fetch(u); } finally { permits.release(); }
        }).toList();
        List<Future<String>> fs = ex.invokeAll(calls, 5, TimeUnit.SECONDS);
        List<String> ok = fs.stream().filter(f -> f.state() == Future.State.SUCCESS).map(Future::resultNow).toList();
    }
    ```

## 10. Dining philosophers

Fix with **resource ordering**: each philosopher picks up the lower-numbered fork first. Alternatives are a `Semaphore(N-1)` "waiter", or
`tryLock` on both forks with backoff.

```java
void dine(int i, ReentrantLock[] forks) {
    ReentrantLock first = forks[Math.min(i, (i + 1) % forks.length)];
    ReentrantLock second = forks[Math.max(i, (i + 1) % forks.length)];
    first.lock(); try { second.lock(); try { eat(i); } finally { second.unlock(); } } finally { first.unlock(); }
}
```

## 11. Count words in 1,000 files in parallel

```java
ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
try (var ex = Executors.newVirtualThreadPerTaskExecutor(); var paths = Files.list(dir)) {
    paths.forEach(p -> ex.submit(() -> {
        try (var lines = Files.lines(p)) {
            lines.flatMap(l -> Arrays.stream(l.split("\\W+"))).filter(w -> !w.isEmpty())
                 .forEach(w -> counts.computeIfAbsent(w.toLowerCase(), k -> new LongAdder()).increment());
        }
        return null;
    }));
}
```

## 12. Implement `CountDownLatch` using `wait/notify`

```java
final class MyLatch {
    private int count;
    MyLatch(int count) { this.count = count; }
    synchronized void countDown() { if (count > 0 && --count == 0) notifyAll(); }
    synchronized void await() throws InterruptedException { while (count > 0) wait(); }
}
```

## 13. Implement a read-write lock

```java
final class SimpleRwLock {
    private int readers, writersWaiting; private boolean writing;
    synchronized void lockRead() throws InterruptedException { while (writing || writersWaiting > 0) wait(); readers++; }
    synchronized void unlockRead() { if (--readers == 0) notifyAll(); }
    synchronized void lockWrite() throws InterruptedException {
        writersWaiting++;
        try { while (writing || readers > 0) wait(); } finally { writersWaiting--; }
        writing = true;
    }
    synchronized void unlockWrite() { writing = false; notifyAll(); }
}
```
Writer preference (`writersWaiting`) prevents writer starvation.

## 14. Parallel merge sort with Fork/Join

```java
class MergeSort extends RecursiveAction {
    final int[] a, tmp; final int lo, hi;
    MergeSort(int[] a, int[] tmp, int lo, int hi) { this.a = a; this.tmp = tmp; this.lo = lo; this.hi = hi; }
    @Override protected void compute() {
        if (hi - lo < 4096) { Arrays.sort(a, lo, hi); return; }
        int mid = (lo + hi) >>> 1;
        invokeAll(new MergeSort(a, tmp, lo, mid), new MergeSort(a, tmp, mid, hi));
        merge(lo, mid, hi);
    }
    void merge(int lo, int mid, int hi) {
        System.arraycopy(a, lo, tmp, lo, hi - lo);
        for (int i = lo, j = mid, k = lo; k < hi; k++)
            a[k] = (j >= hi || (i < mid && tmp[i] <= tmp[j])) ? tmp[i++] : tmp[j++];
    }
}
```

## 15. Hedged request with a timeout

See example 20 in [Worked Examples](../virtual-threads/04-examples.md#20-hedged-request-tail-latency-killer).
