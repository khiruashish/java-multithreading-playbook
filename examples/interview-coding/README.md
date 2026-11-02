# Interview coding solutions

21 multithreading interview problems. Every file is a single-file Java program (JDK 21+) whose `main` hammers the
solution with many threads, asserts the invariant, and prints `PASS`. They're explained in the playbook's **Coding Interview** tab;
the docs include code straight from these files (sections marked `// --8<-- [start:name]`).

```bash
./run-all.sh                      # all 21
java BoundedBlockingQueue.java    # one
```

| # | File | Problem |
|---|---|---|
| 1 | OddEvenPrinter | Two threads alternate odd/even (monitor and semaphore versions) |
| 2 | RoundRobinPrinter | N threads print in rotation (one Condition per thread) |
| 3 | FizzBuzzMultithreaded | LeetCode 1195 |
| 4 | PrintInOrder | LeetCode 1114 (latches, CompletableFuture) |
| 5 | H2OBuilder | LeetCode 1117 (semaphores + CyclicBarrier) |
| 6 | BoundedBlockingQueue | Lock + two Conditions, circular buffer, timed offer |
| 7 | SimpleThreadPool | Workers, FutureTask, rejection, shutdown race |
| 8 | SimpleReadWriteLock | Writer-preferring RW lock (and a JDK 21 VT pinning lesson) |
| 9 | CustomCyclicBarrier | Reusable barrier with generations |
| 10 | DelayedTaskScheduler | PriorityQueue + awaitNanos dispatcher |
| 11 | DeadlockDetectAndFix | Deterministic deadlock, ThreadMXBean detection, two fixes |
| 12 | DiningPhilosophers | Resource ordering and waiter solutions |
| 13 | SleepingBarber | Signalling semaphores |
| 14 | BankTransfer | Ordered locking, conserved totals, consistent audit |
| 15 | ProducerConsumerPoisonPill | 3-stage pipeline, bounded queues, poison pills |
| 16 | ConcurrentLruCache | LinkedHashMap access order + lock, load outside lock |
| 17 | TokenBucketRateLimiter | Lazy refill, blocking and non-blocking acquire |
| 18 | SingleFlightCache | Request coalescing with putIfAbsent + CompletableFuture |
| 19 | ConcurrentWebCrawler | Virtual threads, Phaser completion, semaphores |
| 20 | ParallelMergeSort | Fork/Join RecursiveAction |
| 21 | BestPriceFanOut | CompletableFuture vs virtual threads with a deadline |
