# Virtual Threads: From Basics to Architect

Virtual threads (JEP 444, **final in Java 21**) are the biggest change to Java concurrency since Java 5. This tab is
a nine-level course. Each level assumes the previous ones.

<div class="pb-cards" markdown>

<div class="pb-card" markdown>
<span class="pb-step">1</span>
<span class="pb-icon">:material-sprout:</span>

[L1 · Basics](01-basics.md){ .pb-title }

Create VTs, use the VT executor, explain why they're cheap

<div class="pb-meta"><span class="pb-chip">0–2 yrs</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-step">2</span>
<span class="pb-icon">:material-cog-transfer:</span>

[L2 · How They Work](02-internals.md){ .pb-title }

Explain continuations, mount/unmount, carriers, the scheduler

<div class="pb-meta"><span class="pb-chip">3–5 yrs</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-step">3</span>
<span class="pb-icon">:material-pin:</span>

[L3 · Pinning & JEP 491](03-pinning.md){ .pb-title }

Detect and fix pinning on 21, and explain what changed in 24

<div class="pb-meta"><span class="pb-chip">3–6 yrs</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-step">4</span>
<span class="pb-icon">:material-flask:</span>

[L4 · 25 Worked Examples](04-examples.md){ .pb-title }

Apply VTs to HTTP, JDBC, files, queues, sockets, batch jobs, …

<div class="pb-meta"><span class="pb-chip">2–8 yrs</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-step">5</span>
<span class="pb-icon">:material-shield-half-full:</span>

[L5 · Patterns & Backpressure](05-patterns.md){ .pb-title }

Bulkheads, rate limits, fan-out/fan-in, pipelines, hedging

<div class="pb-meta"><span class="pb-chip">5–9 yrs</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-step">6</span>
<span class="pb-icon">:material-swap-horizontal:</span>

[L6 · Migration Guide](06-migration.md){ .pb-title }

Move a Java 8/11/17 service to VTs safely

<div class="pb-meta"><span class="pb-chip">5–10 yrs</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-step">7</span>
<span class="pb-icon">:material-alert:</span>

[L7 · Pitfalls](07-pitfalls.md){ .pb-title }

Avoid the 17 mistakes teams actually make

<div class="pb-meta"><span class="pb-chip">3–10 yrs</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-step">8</span>
<span class="pb-icon">:material-eye:</span>

[L8 · Observability](08-observability.md){ .pb-title }

Use JFR, thread dumps, and metrics with millions of threads

<div class="pb-meta"><span class="pb-chip">6–12 yrs</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-step">9</span>
<span class="pb-icon">:material-city-variant:</span>

[L9 · Architect Level](09-architect.md){ .pb-title }

Capacity models, platform-wide adoption, VT vs reactive decisions

<div class="pb-meta"><span class="pb-chip">10–15 yrs</span></div>
</div>

</div>

## The idea in one diagram

```mermaid
flowchart TB
    subgraph Before["Java 8–20: thread-per-request with platform threads"]
      R1[Request] --> PT1[OS thread 1 — blocked on DB 95% of the time]
      R2[Request] --> PT2[OS thread 2 — blocked on HTTP]
      R3[Request ...] --> PTN[OS thread 200 — pool exhausted → requests queue]
    end
    subgraph After["Java 21+: thread-per-request with virtual threads"]
      Q1[Request 1] --> V1[VT 1]
      Q2[Request 2] --> V2[VT 2]
      Q3[Request 100 000] --> VN[VT 100 000]
      V1 & V2 & VN -. mounted only while running CPU work .-> C[8 carrier OS threads]
    end
```

## Three sentences to remember

1. A virtual thread is a `java.lang.Thread` that the **JVM** schedules onto a small pool of OS **carrier** threads.
2. When it blocks (socket, `sleep`, lock, queue), it **unmounts**: its stack is saved to the heap and the carrier
   runs another VT.
3. So **blocking becomes cheap**, and the simple thread-per-request style scales like async code. That helps throughput,
   not the latency of a single request.
