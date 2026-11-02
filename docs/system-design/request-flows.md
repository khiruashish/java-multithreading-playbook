# Request Flow Sequences

## Thread-per-request under a slow downstream: Java 8 vs Java 21

```mermaid
sequenceDiagram
    participant U as Users (2000 rps)
    participant T as Tomcat 200 threads (Java 8)
    participant P as Payments (slow: 5 s)
    participant H as Healthy endpoints
    U->>T: /checkout (needs payments)
    T->>P: 200 threads blocked 5 s each
    U->>T: /catalog (healthy!)
    Note over T: no free threads → /catalog ALSO times out<br/>(cascading failure)
```

```mermaid
sequenceDiagram
    participant U as Users (2000 rps)
    participant V as Tomcat VTs (Java 21)
    participant B as Payments bulkhead (50)
    participant P as Payments (slow: 5 s)
    participant C as Catalog
    U->>V: /checkout
    V->>B: tryAcquire(50 ms)
    B-->>V: 50 in use → reject → 503 fast
    U->>V: /catalog
    V->>C: unaffected → 200
    Note over V: failure isolated to the payments path
```

## Checkout saga with parallel validation

```mermaid
sequenceDiagram
    autonumber
    participant API as Checkout API (VT)
    participant SC as Fan-out scope
    participant Inv as Inventory
    participant Pr as Pricing
    participant Pay as Payments
    participant DB as Orders DB (outbox)
    participant K as Kafka
    API->>SC: validate in parallel
    par
      SC->>Inv: reserve(items)
      SC->>Pr: finalQuote(cart)
    end
    SC-->>API: reservation + quote (or fail → release reservation)
    API->>Pay: authorize(amount)  [bulkhead + timeout + idempotency key]
    alt authorized
      API->>DB: TX: insert order + outbox row
      DB-->>K: Debezium relays OrderPlaced
      API-->>API: 201 Created
    else declined / timeout
      API->>Inv: compensate: release(reservation)
      API-->>API: 402 / 504
    end
```

## Asynchronous messaging consumer with per-key ordering

```mermaid
sequenceDiagram
    participant K as Kafka partition 3
    participant C as Consumer (poll loop)
    participant L1 as Lane key=A (VT)
    participant L2 as Lane key=B (VT)
    participant O as Offset tracker
    K->>C: records A1, B1, A2, B2
    C->>L1: A1, A2 (in order)
    C->>L2: B1, B2 (in order)
    L1-->>O: A1 done, A2 done
    L2-->>O: B1 done
    O->>K: commit highest contiguous offset
```

## Scatter-gather search with hedging

```mermaid
sequenceDiagram
    participant API
    participant S1 as Shard 1
    participant S2 as Shard 2
    participant S2b as Shard 2 replica
    par
      API->>S1: query
      API->>S2: query
    end
    S1-->>API: hits (40 ms)
    Note over API,S2: shard 2 slower than p95 (60 ms) → hedge
    API->>S2b: same query
    S2b-->>API: hits (30 ms)
    API--xS2: cancel
    API-->>API: merge top-K
```
