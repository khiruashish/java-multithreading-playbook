# Foundations

<span class="level l1">L1</span> <span class="level l2">L2</span>

Everything else in this playbook, including virtual threads, builds on these six ideas:

1. **Thread**: an independent path of execution that shares heap memory with other threads.
2. **Race condition**: the result depends on how threads interleave.
3. **Visibility**: one thread's writes are not automatically seen by another thread (JMM).
4. **Atomicity**: a compound action (`check-then-act`, `read-modify-write`) must look indivisible.
5. **Ordering**: compilers and CPUs reorder instructions unless a *happens-before* edge forbids it.
6. **Liveness**: correct code can still get stuck (deadlock), spin uselessly (livelock), or never run (starvation).

```mermaid
flowchart LR
    subgraph Problems
      R[Race condition] --- V[Visibility]
      V --- O[Reordering]
      O --- L[Liveness]
    end
    subgraph Tools
      S[synchronized] --- VO[volatile]
      VO --- LK[Locks]
      LK --- AT[Atomics / VarHandle]
      AT --- IM[Immutability]
      IM --- CF[Confinement]
    end
    R -->|fixed by| S & LK & AT
    V -->|fixed by| VO & S & AT
    O -->|fixed by| VO & S
    L -->|avoided by| LK & CF
```
