# Java Multithreading & Concurrency Playbook

A sequential MkDocs playbook that runs from **first thread** to **15-year architect**, covering Java 8 through Java 21 / 24 / 25,
with the deepest focus on **virtual threads**.

| Tab | Contents |
|---|---|
| Home | Learning path (0 → 15 yrs), Java 8 → 25 feature timeline, cheat sheet |
| Foundations | Threads, JMM / happens-before, `synchronized`, locks, atomics / VarHandle, liveness |
| Java 8 Toolkit | Executors, pool sizing, CompletableFuture, Fork/Join, concurrent collections, synchronizers, ThreadLocal |
| Virtual Threads | 9 levels: basics → internals → pinning (JEP 491) → 25 examples → patterns → migration → pitfalls → observability → architect |
| Modern Concurrency | Structured concurrency (21–24 and 25 APIs), scoped values, VT vs reactive, Flow API |
| Production Code | Spring Boot 4.1 + Java 21 code **included from a compiling, tested sample service** |
| System Design | 50 Mermaid diagrams: executor flows, VT scheduling, request sequences, case studies, distributed concurrency |
| Performance | Measured Java 8 vs 21 speed-ups, cost and LOC savings, JMH, jcstress, false sharing |
| Advanced | Lock-free algorithms, thread dumps, pattern catalogue, bugs gallery |
| Interview | 220 questions with answers (170 by level + a 50-question VT deep dive), 15 coding problems, 8 system design rounds |

## Read it

```bash
pip install -r requirements.txt
mkdocs serve          # http://127.0.0.1:8000
mkdocs build --strict # what CI runs
```

## Run the code

```bash
# Spring Boot sample (JDK 21+): 14 tests incl. overload test
cd examples/spring-boot-vt && mvn verify && mvn spring-boot:run

# Single-file demos (JDK 21+)
cd examples/plain-java
java PlatformVsVirtualBenchmark.java
java FanOutComparison.java
java -Djdk.virtualThreadScheduler.parallelism=2 PinningDemo.java
java --enable-preview --source 21 StructuredConcurrencyDemo.java
```

Measured on JDK 21 (4 vCPU): 10k × 100 ms IO tasks take **5,061 ms** on a 200-thread pool vs **224 ms** on virtual threads;
2,000 three-way fan-out requests take **2,695 ms** with Java 8 `CompletableFuture` vs **184 ms** with virtual threads.

## Layout

```
mkdocs.yml                 site config + navigation tabs
docs/                      playbook pages
examples/plain-java/       runnable single-file demos
examples/spring-boot-vt/   production-style Spring Boot service on virtual threads
.github/workflows/ci.yml   strict docs build, sample tests on JDK 21 & 25, demo runs, gh-pages deploy
```
