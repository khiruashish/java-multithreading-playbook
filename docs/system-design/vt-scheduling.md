# Virtual Thread Scheduling Diagrams

## Carrier pool and run queues

```mermaid
flowchart TB
    subgraph Heap
      direction LR
      S1[VT-1 stack chunk<br/>PARKED on socket]:::parked
      S2[VT-2 stack chunk<br/>PARKED on queue.take]:::parked
      S3[VT-3<br/>RUNNABLE]:::runnable
      S4[VT-4<br/>RUNNABLE]:::runnable
    end
    subgraph Scheduler["ForkJoinPool (FIFO, parallelism = #cores)"]
      direction LR
      C1[Carrier-1<br/>running VT-5]
      C2[Carrier-2<br/>running VT-6]
      C3[Carrier-3<br/>idle → steals VT-3]
    end
    subgraph Poller["Read/Write Poller threads (epoll / kqueue / IOCP)"]
      P[fd registrations]
    end
    S3 --> C3
    S1 -. fd registered .-> P
    P -- ready → unpark VT-1 --> Scheduler
    classDef parked fill:#eceff1,stroke:#607d8b
    classDef runnable fill:#e8f5e9,stroke:#2e7d32
```

## State machine of a virtual thread

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> STARTED: start()
    STARTED --> RUNNING: mounted on carrier
    RUNNING --> PARKING: park / blocking op
    PARKING --> PARKED: yield ok → unmounted
    PARKING --> PINNED: cannot yield (native frame, or monitor on JDK ≤23)
    PINNED --> RUNNING: unblocked (carrier was blocked meanwhile)
    PARKED --> RUNNABLE: unpark()
    RUNNABLE --> RUNNING: scheduled on any carrier
    RUNNING --> YIELDING: Thread.yield()
    YIELDING --> RUNNABLE
    RUNNING --> TERMINATED: run() completes
    TERMINATED --> [*]
```

## Blocking socket read without pinning

```mermaid
sequenceDiagram
    autonumber
    participant VT
    participant NIO as NioSocketImpl
    participant Poller
    participant Carrier
    VT->>NIO: read()
    NIO->>NIO: non-blocking read → EAGAIN
    NIO->>Poller: register(fd, VT)
    NIO->>VT: park()
    VT->>Carrier: yield (unmount)
    Carrier->>Carrier: run other VTs
    Poller-->>VT: fd ready → unpark
    VT->>Carrier: mounted (any carrier)
    VT->>NIO: retry read → data
```

## JDK 21 vs JDK 24: blocking inside `synchronized`

```mermaid
sequenceDiagram
    participant VT
    participant Mon as Monitor
    participant Car as Carrier
    rect rgb(255,235,238)
    Note over VT,Car: JDK 21–23
    VT->>Mon: monitorenter (owner = CARRIER)
    VT->>VT: socket read blocks
    Note over Car: VT cannot unmount → carrier blocked (pinned)
    end
    rect rgb(232,245,233)
    Note over VT,Car: JDK 24+ (JEP 491)
    VT->>Mon: monitorenter (owner = VIRTUAL THREAD)
    VT->>VT: socket read blocks
    VT->>Car: unmount, monitor stays owned by VT
    Car->>Car: runs other VTs
    end
```

## Compensation for capturing operations (file IO, `Object.wait` on 21)

```mermaid
flowchart LR
    VT[VT does Files.readAllBytes] --> C1[Carrier-1 captured]
    C1 -->|managedBlock| FJP{ForkJoinPool}
    FJP -->|spawn compensating carrier<br/>up to maxPoolSize 256| C9[Carrier-9]
    C9 --> Others[Other VTs keep running]
```
