# Thread & Executor Diagrams

## ThreadPoolExecutor task submission

```mermaid
sequenceDiagram
    participant Caller
    participant TPE as ThreadPoolExecutor
    participant Q as BlockingQueue (bounded)
    participant W as Worker threads
    participant RH as RejectionHandler
    Caller->>TPE: execute(task)
    alt workers < core
        TPE->>W: addWorker(task, core=true)
    else queue has space
        TPE->>Q: offer(task)
        W->>Q: take() / poll(keepAlive)
        Q-->>W: task
    else workers < max
        TPE->>W: addWorker(task, core=false)
    else saturated
        TPE->>RH: rejectedExecution(task)
        RH-->>Caller: Abort / CallerRuns / Discard
    end
```

## Producer–consumer with a bounded buffer

```mermaid
flowchart LR
    P1[Producer 1] -->|put: blocks when full| Q[(ArrayBlockingQueue<br/>capacity N)]
    P2[Producer 2] -->|put| Q
    Q -->|take: blocks when empty| C1[Consumer 1]
    Q -->|take| C2[Consumer 2]
    Q -->|take| C3[Consumer 3]
    style Q fill:#ffe0b2,stroke:#e65100
```

## Executor lifecycle states

```mermaid
stateDiagram-v2
    [*] --> RUNNING
    RUNNING --> SHUTDOWN: shutdown()
    RUNNING --> STOP: shutdownNow()
    SHUTDOWN --> STOP: shutdownNow()
    SHUTDOWN --> TIDYING: queue empty & no workers
    STOP --> TIDYING: no workers
    TIDYING --> TERMINATED: terminated() hook done
    TERMINATED --> [*]
    note right of RUNNING: close() (Java 19+) = shutdown + awaitTermination\n(+ shutdownNow if interrupted)
```

## CompletableFuture dependency graph

```mermaid
flowchart LR
    A[supplyAsync: cart] --> B[thenCompose: address]
    A --> C[thenCompose: quote]
    B --> D[thenCombine]
    C --> D
    D --> E[exceptionally]
    E --> F[join]
```

## Fork/Join recursive decomposition

```mermaid
flowchart TD
    T[sum 0..1M] --> L[sum 0..500k]
    T --> R[sum 500k..1M]
    L --> LL[0..250k] & LR[250k..500k]
    R --> RL[500k..750k] & RR[750k..1M]
    LL & LR --> L2((join))
    RL & RR --> R2((join))
    L2 & R2 --> TOT((total))
```

## Thread-per-request: platform vs virtual

```mermaid
flowchart TB
    subgraph J8[Java 8 Tomcat]
        direction LR
        AQ[Accept queue] --> TP[200 worker threads]
        TP --> X1[Request blocked on DB<br/>OS thread blocked]
    end
    subgraph J21[Java 21 Tomcat + VTs]
        direction LR
        AC[Acceptor] --> VT[New VT per request]
        VT --> X2[Request blocked on DB<br/>VT parked, carrier free]
        X2 -. resumed .-> CAR[8 carriers]
    end
```
