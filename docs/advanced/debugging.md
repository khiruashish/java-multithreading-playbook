# Debugging & Thread Dumps

<span class="level l3">L3</span> <span class="level l4">L4</span>

## Tools

| Tool | Use |
|---|---|
| `jstack <pid>` / `jcmd <pid> Thread.print` | Platform thread dump with lock info and deadlock detection |
| `jcmd <pid> Thread.dump_to_file -format=json f.json` | **All** threads, including virtual ones |
| `jcmd <pid> JFR.start / JFR.dump` | Flight recording: locks, parks, pinning, IO, GC, CPU |
| async-profiler (`-e lock`, `-e wall`, `-e cpu`) | Lock contention, wall-clock and CPU flame graphs |
| JDK Mission Control | Visualize JFR |
| VisualVM | Live thread states |
| `ThreadMXBean` | Programmatic deadlock detection and CPU time per thread |

## Reading a platform thread dump

```text
"order-worker-3" #41 prio=5 os_prio=0 cpu=1203.44ms elapsed=360.12s tid=0x... nid=0x2b03 waiting for monitor entry
   java.lang.Thread.State: BLOCKED (on object monitor)
        at com.acme.Inventory.reserve(Inventory.java:58)
        - waiting to lock <0x000000071a2b3c40> (a com.acme.Inventory)
        at com.acme.OrderService.place(OrderService.java:31)

"order-worker-7" #45 ... runnable
        at java.net.SocketInputStream.socketRead0(Native Method)
        ...
        at com.acme.Inventory.reserve(Inventory.java:61)
        - locked <0x000000071a2b3c40> (a com.acme.Inventory)      ← holding the lock during socket IO!
```

Diagnosis: a lock is held across a network call, and every other worker is `BLOCKED`. Fix: move the IO outside the critical section.

## Patterns to look for

| Pattern in dump | Meaning |
|---|---|
| Many `BLOCKED` threads on one monitor address | Lock contention or lock held during IO |
| "Found one Java-level deadlock" | Deadlock (jstack prints the cycle) |
| Many `WAITING` in `ThreadPoolExecutor.getTask` | Idle pool (fine) |
| Many `WAITING` in `HikariPool.getConnection` | DB pool exhaustion |
| Many `TIMED_WAITING` in `Thread.sleep` inside retry | Retry storm |
| `RUNNABLE` in `HashMap.getNode` loop with high CPU | Corrupted `HashMap` used concurrently (pre-Java 8 infinite loop; 8+ silent corruption) |
| Carrier threads `ForkJoinPool-1-worker-N` blocked in app code | Pinned VTs (JDK 21–23) |

## Take three dumps, five seconds apart

A single dump is a snapshot. Threads stuck in the **same frame across all three** are the real problem.

```bash
for i in 1 2 3; do jcmd $PID Thread.print -l > dump-$i.txt; sleep 5; done
```

## Finding lock contention with JFR

```bash
jfr print --events jdk.JavaMonitorEnter,jdk.ThreadPark --stack-depth 10 rec.jfr | less
# JMC: Threads → Lock Instances: shows total blocked time per monitor
```

## async-profiler

```bash
asprof -e lock -d 30 -f lock.html <pid>     # who contends on which lock
asprof -e wall -t -d 30 -f wall.html <pid>  # wall clock per thread, shows waiting
```
