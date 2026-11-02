# Production Observability

<span class="level l3">L3</span> <span class="level l4">L4</span> · ✅ Actuator endpoints were queried on the running [Spring Boot sample](../production/sample-project.md)

| Tool | What it gives you |
|---|---|
| **Spring Boot Actuator** `/actuator/threaddump` | Thread dump over HTTP (JSON or text) |
| **Actuator** `/actuator/metrics` + Micrometer | `jvm.threads.*`, executor and custom bulkhead metrics |
| Micrometer `ExecutorServiceMetrics` | Pool size, active, queued, completed, idle |
| Prometheus + Grafana | Time series, dashboards, alerts |
| OpenTelemetry (agent or SDK) | Traces that follow work across threads and services |
| JFR streaming → Micrometer | Pinning and lock-wait counters ([JFR page](jfr-jmc.md)) |

## Actuator thread dump

```yaml
management.endpoints.web.exposure.include: health,info,metrics,prometheus,threaddump
```

```text
$ curl -H 'Accept: text/plain' localhost:8091/actuator/threaddump | head
2026-11-02 07:49:48
Full thread dump OpenJDK 64-Bit Server VM (21.0.12.1+1-1-24.04.4-Ubuntu mixed mode, sharing):

"Reference Handler" - Thread t@9
   java.lang.Thread.State: RUNNABLE
	at java.base@21.0.12.1/java.lang.ref.Reference.waitForReferencePendingList(Native Method)
	...
```

```text
$ curl localhost:8091/actuator/threaddump | jq -c '.threads | length, (group_by(.threadState) | map({state: .[0].threadState, n: length}))'
25
[{"state":"RUNNABLE","n":8},{"state":"TIMED_WAITING","n":10},{"state":"WAITING","n":7}]
```

!!! warning "Actuator, `jvm.threads.live`, and `ThreadMXBean` don't see virtual threads"
    The sample serves every request on a virtual thread, yet the dump lists **25 platform threads**. For virtual threads, use
    `jcmd <pid> Thread.dump_to_file -format=json` ([JDK tools](jdk-cli.md)). Protect `/actuator/threaddump` (it exposes internals) and
    never make it public.

## Metrics that exist out of the box (real list from the sample)

```text
$ curl localhost:8091/actuator/metrics | jq -c '[.names[] | select(test("executor|bulkhead|jvm.threads|virtual"))]'
["bulkhead.in_use","bulkhead.rejected","bulkhead.wait","executor","executor.active","executor.completed","executor.idle",
 "executor.pool.core","executor.pool.max","executor.pool.size","executor.queue.remaining","executor.queued",
 "jvm.threads.daemon","jvm.threads.live","jvm.threads.peak","jvm.threads.started","jvm.threads.states",
 "jvm.virtual_threads.pinned"]
```

* `executor.*` comes from wrapping the CPU pool with `ExecutorServiceMetrics.monitor(registry, pool, "cpu")`.
* `bulkhead.*` and `jvm.virtual_threads.pinned` are the sample's own meters (semaphore bulkheads + the JFR pinning monitor).

```text
$ curl 'localhost:8091/actuator/metrics/bulkhead.wait?tag=name:catalog' | jq -c .measurements
[{"statistic":"COUNT","value":3.0},{"statistic":"TOTAL_TIME","value":0.000041696},{"statistic":"MAX","value":0.000027671}]
```

## Prometheus queries (PromQL) for concurrency dashboards

```promql
# Pool saturation: queued tasks per executor
executor_queued_tasks{name="cpu"}

# Permits in use per bulkhead (compare with its configured max-concurrent), and rejections per second
max_over_time(bulkhead_in_use[5m])
rate(bulkhead_rejected_total[5m])

# p99 time waiting for a bulkhead permit
histogram_quantile(0.99, sum by (le, name) (rate(bulkhead_wait_seconds_bucket[5m])))

# Virtual-thread pinning (should be 0 on hot paths; alert if not)
increase(jvm_virtual_threads_pinned_total[10m]) > 0

# DB pool starvation
hikaricp_connections_pending > 0

# Platform thread leak
deriv(jvm_threads_live_threads[30m]) > 0
```

(Percentile buckets need `management.metrics.distribution.percentiles-histogram.bulkhead.wait=true`.)

## Tracing across threads

OpenTelemetry's Java agent and Micrometer Tracing keep trace context in `ThreadLocal`s. Work you hand to **your own** executors must be wrapped,
or the trace breaks at the thread boundary:

```java
ExecutorService traced = io.opentelemetry.context.Context.taskWrapping(Executors.newVirtualThreadPerTaskExecutor());
// or Micrometer: ContextExecutorService.wrap(executor, () -> ContextSnapshotFactory.builder().build().captureAll())
```
