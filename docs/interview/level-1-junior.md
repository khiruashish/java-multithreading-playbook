# Interview: 0–2 Years

<span class="level l1">L1</span>

## Threads basics

??? question "1. What is the difference between a process and a thread?"
    A process has its own address space. Threads in a process share the heap but each has its own stack and program counter.
    Threads are cheaper to create and to switch between, and they communicate through shared memory, which is why they need synchronization.

??? question "2. What are the ways to create a thread in Java?"
    Extend `Thread`; implement `Runnable` (or `Callable` for a result) and pass it to a `Thread` or executor; and, since Java 21,
    `Thread.ofPlatform()/ofVirtual()` builders, `Thread.startVirtualThread`, and `Executors.newVirtualThreadPerTaskExecutor()`.

??? question "3. `Runnable` vs `Callable`?"
    `Runnable.run()` returns nothing and can't throw checked exceptions. `Callable.call()` returns a value and can throw `Exception`.
    Submitting a `Callable` gives you a `Future<T>`.

??? question "4. What happens if you call `run()` instead of `start()`?"
    `run()` executes on the **current** thread like a normal method call. No new thread is created.

??? question "5. Can you start a thread twice?"
    No. The second `start()` throws `IllegalThreadStateException`.

??? question "6. Name the thread states."
    `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`.

??? question "7. Difference between `BLOCKED` and `WAITING`?"
    `BLOCKED` = waiting to enter a `synchronized` monitor. `WAITING` = waiting indefinitely for another thread's action
    (`wait()`, `join()`, `LockSupport.park()`).

??? question "8. What does `join()` do?"
    It makes the caller wait until the target thread terminates, and it creates a happens-before edge from the thread's actions to the return of `join()`.

??? question "9. `sleep()` vs `wait()`?"
    `sleep` is a static `Thread` method. It pauses without releasing locks. `wait` is an `Object` method that must be called while holding the monitor,
    and it **releases** the monitor until it's notified.

??? question "10. What is a daemon thread?"
    A background thread that doesn't keep the JVM alive. The JVM exits when only daemon threads remain. Virtual threads are always daemons.

??? question "11. What is thread priority, and should you rely on it?"
    A hint (1–10) to the OS scheduler. Don't rely on it: it's platform-dependent, and it's ignored for virtual threads.

??? question "12. How do you stop a thread?"
    Cooperatively: `interrupt()` it, and have the thread check `isInterrupted()` or handle `InterruptedException`. `Thread.stop()` is
    unsupported.

## Synchronization

??? question "13. What is a race condition?"
    When correctness depends on the timing or interleaving of threads, such as two threads doing `count++` and losing an update.

??? question "14. Why is `count++` not thread-safe?"
    It's three steps: read, add, write. Two threads can read the same value, and one increment is lost.

??? question "15. What does `synchronized` guarantee?"
    Mutual exclusion on a monitor, plus visibility: an unlock happens-before the next lock of the same monitor.

??? question "16. Synchronized method vs synchronized block?"
    A method locks `this` (or the `Class` for static methods) for the whole body. A block lets you choose the lock object and narrow the scope,
    which is usually better.

??? question "17. What is `volatile`?"
    A field modifier that guarantees visibility (reads see the latest write) and ordering (no reordering across it). It doesn't
    make compound actions atomic.

??? question "18. Is `synchronized` reentrant?"
    Yes. A thread holding a monitor can enter other blocks synchronized on the same monitor.

??? question "19. What is a deadlock? Give an example."
    Two or more threads each wait for a lock the other holds. T1 holds A and wants B, while T2 holds B and wants A.

??? question "20. How do you prevent deadlock?"
    Acquire locks in a consistent global order, use `tryLock` with timeouts, hold fewer locks, or avoid shared mutable state.

??? question "21. What is `wait()/notify()` used for? Why call `wait` in a loop?"
    Guarded waiting for a condition. Use a loop because of spurious wakeups, and because the condition may change again before the thread reacquires the lock.

??? question "22. `notify()` vs `notifyAll()`?"
    `notify` wakes one arbitrary waiter. `notifyAll` wakes all of them. Prefer `notifyAll` unless every waiter waits for the same condition.

??? question "23. What is thread safety?"
    A class is thread-safe if it behaves correctly when accessed from multiple threads with no extra synchronization by callers.

??? question "24. Why are immutable objects thread-safe?"
    Their state can't change after construction (with `final` fields and no escaping `this`), so there's nothing to race on.

## Executors and collections

??? question "25. Why use an `ExecutorService` instead of creating threads?"
    It separates task submission from execution policy, lets you reuse platform threads, bounds concurrency, manages lifecycle, and returns `Future`s.

??? question "26. `submit()` vs `execute()`?"
    `execute(Runnable)` returns nothing, and exceptions go to the uncaught exception handler. `submit` returns a `Future`, and exceptions are captured
    in it.

??? question "27. `shutdown()` vs `shutdownNow()`?"
    `shutdown` stops accepting tasks and lets queued tasks finish. `shutdownNow` also interrupts running tasks and returns the queued ones.

??? question "28. What does `Future.get()` do?"
    It blocks until the task completes, then returns the result or throws `ExecutionException` (failure), `CancellationException`, or
    `InterruptedException`. There's also a timeout overload.

??? question "29. `HashMap` vs `Hashtable` vs `ConcurrentHashMap`?"
    `HashMap` isn't thread-safe. `Hashtable` synchronizes every method (slow, legacy). `ConcurrentHashMap` uses fine-grained
    locking and CAS, has lock-free reads, and provides atomic `compute*`/`merge`.

??? question "30. What is `CopyOnWriteArrayList` good for?"
    Read-mostly lists such as listeners. Every write copies the array, and iterators see a snapshot.

??? question "31. What is a `BlockingQueue`?"
    A queue whose `put` blocks when it's full and whose `take` blocks when it's empty. It's the building block for producer–consumer designs.

??? question "32. What is `AtomicInteger`?"
    A lock-free integer that uses CAS for atomic operations like `incrementAndGet`.

??? question "33. What does `CountDownLatch` do?"
    It lets threads wait until a count reaches zero. It's one-shot. Use it, for example, to wait for N tasks to finish.

## Virtual threads (Java 21)

??? question "34. What is a virtual thread?"
    A lightweight `Thread` that the JVM schedules onto a small pool of OS carrier threads. Blocking unmounts it, so millions can exist.

??? question "35. How do you create one?"
    `Thread.ofVirtual().start(r)`, `Thread.startVirtualThread(r)`, or `Executors.newVirtualThreadPerTaskExecutor()`.

??? question "36. Are virtual threads faster?"
    Not per task. They raise **throughput** for IO-bound concurrent work. CPU-bound work gets no speed-up.

??? question "37. Should you pool virtual threads?"
    No. They're cheap to create, so you create one per task. Limit concurrency with semaphores instead.

??? question "38. How do you enable virtual threads in Spring Boot?"
    Use Spring Boot 3.2+ on Java 21+ and set `spring.threads.virtual.enabled=true`.

??? question "39. How do you check whether code runs on a virtual thread?"
    `Thread.currentThread().isVirtual()`.

??? question "40. What does `try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {...}` do at the end of the block?"
    `close()` shuts down the executor and waits for all submitted tasks to complete. `ExecutorService` is `AutoCloseable` since Java 19.
