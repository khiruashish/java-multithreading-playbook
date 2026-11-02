# Interview Preparation

Questions are grouped by the experience level an interviewer **expects** you to handle. Every answer is collapsed, so
you can test yourself first.

| Page | Questions | Focus |
|---|---|---|
| [0–2 Years](level-1-junior.md) | 40 | Threads, `synchronized`, `volatile`, executors, VT basics |
| [3–5 Years](level-2-mid.md) | 45 | JMM, locks, atomics, collections, `CompletableFuture`, pool sizing, Spring + VTs |
| [6–9 Years](level-3-senior.md) | 45 | Internals, pinning, structured concurrency, debugging, performance, design trade-offs |
| [10–15 Years](level-4-architect.md) | 40 | Platform strategy, capacity, failure isolation, distributed concurrency, migration leadership |
| [Virtual Threads Deep-Dive](virtual-threads-qa.md) | 50 | From "what is it" to scheduler internals and architecture |
| [Coding Problems](coding-problems.md) | 15 | Write-on-the-whiteboard problems with Java 8 and 21 solutions |
| [System Design Rounds](system-design-rounds.md) | 8 | Concurrency-heavy design prompts with model answers |

## How interviewers grade concurrency answers

| Signal | Junior | Senior | Staff/Architect |
|---|---|---|---|
| Correctness vocabulary | Race, deadlock | Happens-before, linearizability | Consistency models across services |
| Tool choice | Knows the APIs | Explains trade-offs | Sets platform defaults and guardrails |
| Failure thinking | "Add try/catch" | Timeouts, cancellation, backpressure | Blast radius, isolation, capacity models |
| Evidence | – | "I'd profile with JFR" | "Here's how we measured and rolled it out" |
| Virtual threads | What they are | Pinning, limits, migration | When **not** to use them; org-wide adoption |

!!! tip "The answer structure that works"
    **Definition → mechanism → example → trade-off → what changed in Java 21/24.**
    For example: "`volatile` gives visibility and ordering through a happens-before edge between a write and later reads, which is
    why the stop-flag example works. It doesn't give atomicity, so `count++` still races. Nothing about it changed with VTs."
