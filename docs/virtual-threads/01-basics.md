# L1: Virtual Thread Basics

<span class="level l1">L1 · 0–2 yrs</span>

## What problem do they solve?

A typical web request spends **~5% of its time on CPU** and **~95% waiting** on databases, HTTP, or caches. With platform
threads, every waiting request holds an OS thread with about 1 MB of reserved stack. A server tops out at a few thousand
concurrent requests, long before CPU is saturated.

Virtual threads make waiting free, so you can have **one thread per concurrent task**, even a million of them.

## Five ways to create a virtual thread

```java
// 1. Fire and forget
Thread.startVirtualThread(() -> System.out.println("hello from " + Thread.currentThread()));

// 2. Builder: named, started
Thread t = Thread.ofVirtual().name("worker-1").start(() -> doWork());
t.join();

// 3. Builder: unstarted
Thread u = Thread.ofVirtual().unstarted(() -> doWork());
u.start();

// 4. Factory: names get a counter: io-0, io-1, ...
ThreadFactory f = Thread.ofVirtual().name("io-", 0).factory();

// 5. Executor: the one you will use 90% of the time
try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
    ex.submit(() -> doWork());
}
```

## Hello, one million threads

```java
public class MillionThreads {
    public static void main(String[] args) {
        long start = System.nanoTime();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, 1_000_000).forEach(i ->
                ex.submit(() -> { Thread.sleep(Duration.ofSeconds(1)); return i; }));
        }
        System.out.printf("1M tasks, each sleeping 1s, finished in %d ms%n",
                (System.nanoTime() - start) / 1_000_000);
    }
}
```

**Measured** (`examples/plain-java/MillionThreads.java`, JDK 21, 4-vCPU container): 200,000 tasks in **2.5 s**, and
1,000,000 tasks in **9.6 s** (most of that is creating and scheduling a million objects on 4 cores). With
`Executors.newFixedThreadPool(200)` it takes `1_000_000 / 200 × 1 s ≈ 83 minutes`. Try `newCachedThreadPool()` and you get
`OutOfMemoryError: unable to create native thread`.

Runnable version: `examples/plain-java/MillionThreads.java`.

## Platform vs virtual: side by side

| | Platform thread | Virtual thread |
|---|---|---|
| Created by | `new Thread()`, `Thread.ofPlatform()` | `Thread.ofVirtual()`, `Thread.startVirtualThread()` |
| Backed by | 1 OS thread for its whole life | Any carrier, only while running |
| Stack | Fixed reservation (default 512 KB–1 MB) | Growable stack chunks on the heap |
| Creation | ~µs to 100 µs (syscall) | ~sub-µs (object allocation) |
| Daemon | Configurable | **Always daemon** |
| Priority | Configurable | Always `NORM_PRIORITY` (`setPriority` is ignored) |
| Thread group | Configurable | A fixed "VirtualThreads" group |
| `isVirtual()` | `false` | `true` |
| Pool it? | Yes | **Never** |

## What virtual threads are NOT

* **Not faster threads.** A single request's latency stays the same. You gain **throughput**: more concurrent requests on the
  same hardware.
* **Not for CPU-bound work.** With 8 cores you get 8 units of compute, whether 8 or 8 million threads are runnable.
* **Not green threads from Java 1.1.** Those were M:1 (all on one OS thread). VTs are M:N, with multi-core work stealing.
* **Not coroutines you must mark.** No `async/await` keywords and no colored functions. Existing blocking code just works.

## Example: an echo server, old vs new

=== "Java 8 — bounded pool"

    ```java
    ExecutorService pool = Executors.newFixedThreadPool(200);   // max 200 concurrent clients
    try (ServerSocket server = new ServerSocket(9000)) {
        while (true) {
            Socket s = server.accept();
            pool.execute(() -> handle(s));                       // client #201 waits in the queue
        }
    }
    ```

=== "Java 21 — thread per connection"

    ```java
    try (ServerSocket server = new ServerSocket(9000);
         var ex = Executors.newVirtualThreadPerTaskExecutor()) {
        while (true) {
            Socket s = server.accept();
            ex.submit(() -> handle(s));                          // 100k concurrent clients is fine
        }
    }

    static void handle(Socket s) {
        try (s; var in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             var out = new PrintWriter(s.getOutputStream(), true)) {
            for (String line; (line = in.readLine()) != null; ) out.println(line);  // blocking read: VT unmounts
        } catch (IOException e) { /* client went away */ }
    }
    ```

The handler code is **identical**. Only the executor changed, and capacity went from 200 clients to around 100,000.

## Quiz (answers in [Interview → VT Q&A](../interview/virtual-threads-qa.md))

1. Why does `main` exit immediately after `Thread.startVirtualThread(...)` unless you `join()`?
2. Will a CPU-bound loop run faster on 1,000 virtual threads than on 8 platform threads?
3. Why is `Executors.newFixedThreadPool(100, Thread.ofVirtual().factory())` an anti-pattern?
