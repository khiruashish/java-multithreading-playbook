# Java Flight Recorder & Mission Control

<span class="level l3">L3</span> · ✅ JFR output on this page is real (JDK 21, `TroubledService`) · JMC is a GUI, described but not run here

**JFR** is a recorder built into the JVM, with about 1% overhead in its default configuration and safe for production. It records events: lock waits, parks,
virtual-thread pinning, CPU samples, GC, IO. **JMC** is the desktop GUI for exploring `.jfr` files. Since JDK 21, the `jfr view`
command gives you most of JMC's summary tables in the terminal.

## Starting a recording

| How | Command |
|---|---|
| At JVM start | `java -XX:StartFlightRecording=duration=60s,filename=rec.jfr,settings=profile -jar app.jar` |
| Continuous ring buffer (production) | `-XX:StartFlightRecording=disk=true,maxage=1h,maxsize=250m,dumponexit=true,filename=/dumps/app.jfr` |
| On a running JVM | `jcmd <pid> JFR.start name=lab settings=profile duration=20s filename=lab.jfr` |
| Dump the ring buffer now | `jcmd <pid> JFR.dump name=1 filename=now.jfr` |
| Stop | `jcmd <pid> JFR.stop name=lab` |

`settings=default` costs about 1% and is suitable for always-on. `settings=profile` gives more detail (lower thresholds, more stack traces) at about 2%.

```text
$ jcmd 717 JFR.start name=s settings=profile duration=15s filename=s.jfr
Started recording 1. The result will be written to: s.jfr
```

## Concurrency events

| Event | Fires when | Default threshold |
|---|---|---|
| `jdk.JavaMonitorEnter` | A thread waited to enter `synchronized` | 20 ms (10 ms in profile) |
| `jdk.JavaMonitorWait` | `Object.wait()` | 20 ms |
| `jdk.ThreadPark` | `LockSupport.park` (j.u.c locks, queues, semaphores) | 20 ms |
| `jdk.VirtualThreadPinned` | A virtual thread blocked while pinned | 20 ms |
| `jdk.VirtualThreadSubmitFailed` | The scheduler couldn't start/unpark a VT | Always |
| `jdk.VirtualThreadStart/End` | VT lifecycle | Off by default (high volume) |
| `jdk.ExecutionSample` | CPU sample of a running thread | 10–20 ms period |
| `jdk.ThreadCPULoad` | Per-thread CPU | 10 s period |
| `jdk.ThreadDump` | Periodic thread dump | Off by default |

## `jfr summary`: what's in the file

```text
$ jfr summary s.jfr        (concurrency lines)
 jdk.JavaMonitorEnter                     4678        110167
 jdk.ThreadPark                           1581         48709
 jdk.VirtualThreadPinned                  1190         17310
 jdk.ExecutionSample                      1131         12281
```

Columns: event type, count, size in bytes. 4,678 monitor waits and 1,190 pinning events in 15 seconds are both red flags.

## `jfr view`: instant answers (JDK 21+)

`jfr view --help` lists about 50 views. These four answer most concurrency questions:

```text
$ jfr view pinned-threads s.jfr
                                            Pinned Virtual Threads
Method                                                         Pinned Count Longest Pinning Total Time Pinned
-------------------------------------------------------------- ------------ --------------- -----------------
TroubledService.lambda$main$3(Object)                                 1,190         66.5 ms            59.7 s
```

```text
$ jfr view contention-by-site s.jfr
                                              Contention by Site
StackTrace                                                                              Count    Avg.    Max.
--------------------------------------------------------------------------------------- ----- ------- -------
TroubledService.lambda$main$2()                                                         4,677 15.7 ms 60.0 ms
```

```text
$ jfr view hot-methods s.jfr
                                     Java Methods that Executes the Most
Method                                                                                        Samples Percent
--------------------------------------------------------------------------------------------- ------- -------
TroubledService.lambda$main$2()                                                                   767  67.82%
TroubledService.lambda$main$5()                                                                   364  32.18%
```

```text
$ jfr view thread-cpu-load lab.jfr
Thread                         System   User
------------------------------ ------ ------
cpu-burner                      0.18% 24.41%
C2 CompilerThread1              0.68%  8.78%
hot-lock-0                      1.30%  2.52%
hot-lock-5                      1.51%  2.47%
...
```

**Reading them together:** `lambda$main$3` (sleep inside `synchronized`) pinned virtual threads for a total of 59.7 s in a 15 s window.
`lambda$main$2` is both the most contended site **and** the hottest method, which is typical when work is done inside a lock.
`cpu-burner` uses a quarter of the machine.

Other useful views: `contention-by-thread`, `contention-by-class`, `thread-count`, `latencies-by-type`, `allocation-by-thread`.

### Live views without a file: `jcmd JFR.view`

```text
$ jcmd 770 JFR.view thread-count
                              Java Thread Statistics
Time         Active Threads   Daemon Threads   Accumulated Threads   Peak Threads
---------- ---------------- ---------------- --------------------- --------------
07:38:11                 26               14                    27             27
```

!!! warning "Thread counts don't include virtual threads"
    This process had 10,000+ virtual threads, but `thread-count` (like `jvm.threads.live` in Micrometer) reports **26**: platform threads only.
    Count virtual threads with a JSON thread dump, or with your own gauge (for example, `ThreadPerTaskExecutor` sizes, or in-flight semaphore permits).

## `jfr print`: raw events with stacks

```text
$ jfr print --events jdk.VirtualThreadPinned --stack-depth 6 lab.jfr
jdk.VirtualThreadPinned {
  startTime = 07:36:19.831 (2026-11-02)
  duration = 50.0 ms
  eventThread = "" (javaThreadId = 32, virtual)
  stackTrace = [
    java.lang.VirtualThread.parkOnCarrierThread(boolean, long) line: 689
    java.lang.VirtualThread.parkNanos(long) line: 648
    java.lang.VirtualThread.sleepNanos(long) line: 807
    java.lang.Thread.sleep(Duration) line: 590
    TroubledService.lambda$main$3(Object) line: 56
    java.util.concurrent.FutureTask.run() line: 317
  ]
}
```

`parkOnCarrierThread` is the giveaway: the virtual thread parked **on** its carrier instead of unmounting. The first application
frame (`TroubledService…line 56`, which is line 60 in the current file) is what you fix.

Other flags: `--json` (machine-readable), `--categories 'Java Application'`, and `jfr print --events jdk.JavaMonitorEnter --stack-depth 10`.

## In-process streaming (Java 14+)

Turn events into metrics and alerts without files, as the Spring sample's `PinningMonitor` does:

```java
try (var rs = new RecordingStream()) {
    rs.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ofMillis(10)).withStackTrace();
    rs.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(50));
    rs.onEvent("jdk.VirtualThreadPinned", e -> pinnedCounter.increment());
    rs.onEvent("jdk.JavaMonitorEnter", e -> log.warn("lock wait {} ms on {}", e.getDuration().toMillis(),
            e.getClass("monitorClass").getName()));
    rs.startAsync();
}
```

## JDK Mission Control (GUI)

Download from Adoptium or Oracle (`jmc`), then open a `.jfr` file:

| JMC page | Concurrency use |
|---|---|
| **Automated Analysis** | Red/yellow rules: "Java Blocking", "Thread contention", "VirtualThreadPinned" |
| **Threads** | Timeline per thread: running / blocked / parked colours |
| **Lock Instances** | Which monitor objects have the most total blocked time, and who held them |
| **Method Profiling** | Flame graph of CPU samples |
| **Event Browser** | Any event type with its stack traces (e.g. `Virtual Thread Pinned`) |

## Custom events for your own code

```java
@Name("com.acme.BulkheadWait") @Label("Bulkhead Wait") @Category("Acme")
class BulkheadWaitEvent extends jdk.jfr.Event {
    @Label("Bulkhead") String bulkhead;
}
var e = new BulkheadWaitEvent();
e.begin();
permits.acquire();
e.bulkhead = "payments";
e.commit();                // appears in JMC and jfr print next to the JDK events
```
