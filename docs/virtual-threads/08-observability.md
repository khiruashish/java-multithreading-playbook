# L8: Observability & Debugging Virtual Threads

<span class="level l3">L3 · 6–9 yrs</span> <span class="level l4">L4</span>

## Thread dumps with millions of threads

`jstack` and `ThreadMXBean` show **platform threads only** (including carriers). For virtual threads, use:

```bash
jcmd <pid> Thread.dump_to_file -format=json /tmp/threads.json   # JSON: every VT, grouped by container
jcmd <pid> Thread.dump_to_file /tmp/threads.txt                 # plain text
```

The JSON groups threads by **thread container** (executor or `StructuredTaskScope`), so you can see the tree of
parent and child tasks. Parked VTs that aren't referenced from anywhere may be omitted.

```json
{ "threadDump": { "threadContainers": [
  { "container": "java.util.concurrent.ThreadPerTaskExecutor@5a2e4553",
    "parent": "<root>",
    "threads": [
      { "tid": "52", "virtual": true, "name": "order-17",
        "stack": [ "java.base/jdk.internal.misc.Unsafe.park(Native Method)",
                   "java.base/java.util.concurrent.locks.LockSupport.park(LockSupport.java:371)",
                   "com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:162)", "..." ] } ] } ] } }
```

Here, `HikariPool.getConnection` shows up in thousands of VT stacks, which means **connection pool exhaustion**, not a JVM problem.

## JFR events for virtual threads

| Event | Default | Meaning |
|---|---|---|
| `jdk.VirtualThreadStart` / `End` | Disabled | Lifecycle (high volume) |
| `jdk.VirtualThreadPinned` | Enabled, 20 ms threshold | Blocked while pinned |
| `jdk.VirtualThreadSubmitFailed` | Enabled | Scheduler rejected the start/unpark (resource exhaustion) |

```bash
java -XX:StartFlightRecording=filename=app.jfr,settings=profile,duration=5m -jar app.jar
jfr summary app.jfr
jfr print --events jdk.VirtualThreadPinned --stack-depth 20 app.jfr
```

JDK Mission Control 9 (JMC) visualizes these events.

## Metrics to export

| Metric | Source | Alert when |
|---|---|---|
| Pinned events per minute | JFR streaming → Micrometer counter | > 0 on hot paths |
| Carrier pool parallelism / active | `jdk.management.VirtualThreadSchedulerMXBean` (JDK 24+) | Sustained full utilization |
| Semaphore permits available | Your bulkheads | Close to 0 |
| Hikari `active`, `pending` | HikariCP Micrometer | pending > 0 sustained |
| In-flight requests | Admission filter | Close to limit |
| Heap after GC | JVM metrics | Growth trend (stuck VTs) |

```java
// JDK 24+: scheduler MXBean
var mx = ManagementFactory.getPlatformMXBean(jdk.management.VirtualThreadSchedulerMXBean.class);
log.info("parallelism={} pool={} mounted={} queued={}",
        mx.getParallelism(), mx.getPoolSize(), mx.getMountedVirtualThreadCount(), mx.getQueuedVirtualThreadCount());
```

## Debugging

* IntelliJ IDEA and Eclipse step through VT code normally. Breakpoints suspend the VT.
* Stack traces are **complete and sequential**, which is a huge win over `CompletableFuture`/reactive traces full of
  scheduler frames.
* Name the VTs you create (`Thread.ofVirtual().name("checkout-", 0)`). Tomcat names request VTs such as `tomcat-handler-N`.

## Distributed tracing

OpenTelemetry's Java agent (1.32+) and Micrometer Tracing propagate context through `ThreadLocal`, which works on VTs.
When you create VTs yourself, wrap the tasks so context propagates:

```java
ExecutorService ex = Context.taskWrapping(Executors.newVirtualThreadPerTaskExecutor()); // OpenTelemetry
// Micrometer: ContextExecutorService.wrap(executor, () -> ContextSnapshotFactory.builder().build().captureAll())
```
