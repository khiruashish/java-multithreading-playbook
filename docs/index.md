---
title: Home
hide:
  - toc
---

<div class="pb-hero" markdown>
<div markdown>

<span class="pb-kicker">Java 8 → 21 · 24 · 25 LTS</span>

<h1>Java Multithreading &amp; Concurrency Playbook</h1>

<p class="pb-lead">From <b>"what is a thread?"</b> to <b>"design the concurrency model for a 50k-RPS platform"</b>.
Every concurrency API from Java 8 to Java 25, with virtual threads taught in nine levels, runnable code,
and real measurements.</p>

[Start the learning path](getting-started/learning-path.md){ .md-button .md-button--primary }
[Virtual threads](virtual-threads/index.md){ .md-button }
[Coding interview](coding-interview/index.md){ .md-button }

</div>
<div class="pb-stack" aria-label="Illustration: virtual threads are scheduled onto carrier threads, which run on CPU cores">
<div class="pb-stack-plane">
<div class="pb-layer l-cpu">CPU cores<small>8 hardware threads</small></div>
<div class="pb-layer l-car">Carrier threads<small>1 per core · OS threads</small></div>
<div class="pb-layer l-fjp">ForkJoinPool scheduler<small>mount · unmount · steal</small></div>
<div class="pb-layer l-vt">Virtual threads<small>millions · cheap to block</small></div>
</div>
<div class="pb-stack-legend">How Java 21 runs a million threads<br>on a handful of OS threads</div></div>
</div>

<div class="pb-stats">
<div class="pb-stat"><b>22×</b><span>faster on 10k blocking tasks (measured, VT vs 200-thread pool)</span></div>
<div class="pb-stat"><b>21</b><span>coding problems with tested solutions &amp; walkthroughs</span></div>
<div class="pb-stat"><b>220+</b><span>interview Q&amp;A from 0 to 15 years</span></div>
<div class="pb-stat"><b>100+</b><span>sequence, state &amp; flow diagrams</span></div>
</div>

## Choose where to start

<div class="pb-cards" markdown>

<div class="pb-card" markdown>
<span class="pb-icon">:material-sprout:</span>

[Foundations](foundations/index.md){ .pb-title }

Threads, lifecycle, the Java Memory Model, `synchronized`, locks, atomics, deadlock and livelock.

<div class="pb-meta"><span class="pb-chip">L1–L2</span><span class="pb-chip">6 pages</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-toolbox:</span>

[Java 8 Toolkit](java8-toolkit/index.md){ .pb-title }

Executors, pool sizing, `CompletableFuture`, Fork/Join, concurrent collections, synchronizers, `ThreadLocal`.

<div class="pb-meta"><span class="pb-chip">L2–L3</span><span class="pb-chip">7 pages</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-lightning-bolt:</span>

[Virtual Threads](virtual-threads/index.md){ .pb-title }

Nine levels from first virtual thread to architect: internals, pinning, 25 examples, migration, pitfalls.

<div class="pb-meta"><span class="pb-chip">L1–L4</span><span class="pb-chip">core track</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-family-tree:</span>

[Modern Java](modern/index.md){ .pb-title }

Structured concurrency (21–24 and 25 APIs), scoped values, virtual threads vs reactive, Flow API.

<div class="pb-meta"><span class="pb-chip">L3–L4</span><span class="pb-chip">Java 21–25</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-leaf:</span>

[Spring Boot](production/index.md){ .pb-title }

Production code from a tested service: fan-out with deadlines, bulkheads, `@Async`, Kafka, JDBC, metrics.

<div class="pb-meta"><span class="pb-chip">L2–L4</span><span class="pb-chip">Boot 4.1</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-sitemap:</span>

[System Design](system-design/index.md){ .pb-title }

Executor and scheduling diagrams, request flows, case studies, distributed locks and idempotency.

<div class="pb-meta"><span class="pb-chip">L3–L4</span><span class="pb-chip">diagrams</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-speedometer:</span>

[Performance](performance/index.md){ .pb-title }

How much time and money Java 21 saves over Java 8, with measured numbers. JMH, jcstress, false sharing.

<div class="pb-meta"><span class="pb-chip">L3–L4</span><span class="pb-chip">measured</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-brain:</span>

[Advanced](advanced/index.md){ .pb-title }

Lock-free algorithms, reading thread dumps, 22 concurrency patterns, and a gallery of 17 real bugs.

<div class="pb-meta"><span class="pb-chip">L3–L4</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-wrench:</span>

[Tools](tools/index.md){ .pb-title }

jcmd, JFR, async-profiler, JMH, jcstress, Lincheck, SpotBugs, Error Prone and more, with real output.

<div class="pb-meta"><span class="pb-chip">L2–L4</span><span class="pb-chip">hands-on</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-code-braces:</span>

[Coding Interview](coding-interview/index.md){ .pb-title }

21 classic problems (odd/even, H2O, blocking queue, thread pool, philosophers…) explained line by line.

<div class="pb-meta"><span class="pb-chip">L1–L4</span><span class="pb-chip">runnable</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-account-tie:</span>

[Interview Q&A](interview/index.md){ .pb-title }

220 questions with answers by level, a virtual-threads deep dive, and system design rounds.

<div class="pb-meta"><span class="pb-chip">All levels</span></div>
</div>

<div class="pb-card" markdown>
<span class="pb-icon">:material-map-marker-path:</span>

[Learning path](getting-started/learning-path.md){ .pb-title }

A 12-week plan, a skills checklist, and what each experience level should master.

<div class="pb-meta"><span class="pb-chip">Start here</span></div>
</div>

</div>

## Experience levels

Every page is tagged with the level it's written for. Read top to bottom the first time, then come back to pages as reference.

<span class="level l1">L1 · 0–2 yrs</span>
<span class="level l2">L2 · 3–5 yrs</span>
<span class="level l3">L3 · 6–9 yrs</span>
<span class="level l4">L4 · 10–15 yrs</span>

## The story in one minute

Java 8 gave us **composable asynchrony** (`CompletableFuture`, parallel streams, `StampedLock`, `LongAdder`),
but every blocking call still tied up an expensive **platform (OS) thread**. So high-throughput services moved to **reactive**
code, which scales but is hard to write, debug, and profile. **Java 21** made **virtual threads** final: cheap, JVM-scheduled
threads, so plain blocking code now scales like reactive code. **Java 24** removed the biggest caveat: `synchronized` no
longer **pins** a virtual thread to its carrier. **Structured concurrency** (preview) and **scoped values** (final in Java 25)
finish the picture.

<div class="pb-timeline">
<div class="pb-tl-item"><span class="pb-tl-year">Java 5 · 2004</span><b>java.util.concurrent</b><span>Executors, locks, atomics, the modern memory model (JSR-133)</span></div>
<div class="pb-tl-item"><span class="pb-tl-year">Java 7 · 2011</span><b>Fork/Join</b><span>Work stealing, Phaser, TransferQueue</span></div>
<div class="pb-tl-item"><span class="pb-tl-year">Java 8 · 2014</span><b>CompletableFuture</b><span>Parallel streams, StampedLock, LongAdder, ConcurrentHashMap rewrite</span></div>
<div class="pb-tl-item"><span class="pb-tl-year">Java 9 · 2017</span><b>Flow API &amp; VarHandle</b><span>CompletableFuture timeouts, onSpinWait</span></div>
<div class="pb-tl-item is-key"><span class="pb-tl-year">Java 21 LTS · 2023</span><b>Virtual threads final</b><span>Structured concurrency and scoped values in preview</span></div>
<div class="pb-tl-item is-key"><span class="pb-tl-year">Java 24 · 2025</span><b>No pinning on synchronized</b><span>JEP 491: monitors owned by the virtual thread</span></div>
<div class="pb-tl-item is-key"><span class="pb-tl-year">Java 25 LTS · 2025</span><b>Scoped values final</b><span>Structured concurrency 5th preview, new Joiner API</span></div>
</div>

## Run it yourself

```bash
pip install -r requirements.txt
mkdocs serve        # http://127.0.0.1:8000
```

Runnable examples live in [`examples/`](https://github.com/khiruashish/java-multithreading-playbook/tree/main/examples):

* `examples/plain-java`: single-file programs you run with `java File.java` on JDK 21+.
* `examples/interview-coding`: 21 self-checking interview solutions (`./run-all.sh`).
* `examples/tools-lab`: a broken target service to practise diagnostics on, plus JMH, jcstress, and testing/static-analysis projects.
* `examples/spring-boot-vt`: a complete Spring Boot service with virtual threads, a fan-out aggregator,
  bulkheads, scoped request context, metrics, and tests.
