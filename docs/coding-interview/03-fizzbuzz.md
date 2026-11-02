# 3. Multithreaded FizzBuzz

<span class="level l1">L1</span> ★★☆ · generic turn-taking monitor · LeetCode 1195

## Problem

> Four threads share one `FizzBuzz` object. Thread A calls `fizz()` (prints "Fizz" for numbers divisible by 3 but not 5),
> B calls `buzz()` (divisible by 5 but not 3), C calls `fizzbuzz()` (divisible by 15), and D calls `number()` (all others).
> Together they must print the normal FizzBuzz sequence for 1..n.

## Key idea

This is the same pattern as odd/even, but with **four predicates**. Each number belongs to exactly one thread. Write the
"wait for my turn → print → advance → wake others" loop **once** and pass in the predicate and the renderer.

```mermaid
flowchart LR
    I[current = i] --> P{which predicate<br/>matches i?}
    P -->|i%15==0| FB[fizzbuzz thread]
    P -->|i%3==0| F[fizz thread]
    P -->|i%5==0| B[buzz thread]
    P -->|otherwise| N[number thread]
    FB & F & B & N -->|print, current++, notifyAll| I
```

```mermaid
sequenceDiagram
    participant N as number
    participant F as fizz
    participant B as buzz
    participant M as monitor(current)
    N->>M: 1 → "1", 2 → "2", current=3, notifyAll
    N->>M: 3 not mine → wait
    F->>M: 3 is mine → "Fizz", current=4, notifyAll
    B->>M: 4 not mine → wait
    N->>M: 4 → "4", current=5, notifyAll
    B->>M: 5 → "Buzz", current=6, notifyAll
```

## Code walkthrough

```java
--8<-- "examples/interview-coding/FizzBuzzMultithreaded.java:core"
```

| Code | Why |
|---|---|
| `private synchronized void loop(IntPredicate mine, IntFunction<String> render)` | One method holds the entire protocol. `synchronized` on the method locks `this`, which is the same monitor that `wait`/`notifyAll` use. |
| `while (current <= n && !mine.test(current)) wait();` | Each thread sleeps until the current number is **its** number, or until the sequence is finished. |
| `notifyAll()` (not `notify()`) | With four threads, `notify()` might wake e.g. `buzz` when the next number belongs to `number`. `buzz` sees it's not its turn and goes back to sleep, and **nobody else was woken**, so the program hangs. `notifyAll` wakes everyone, and exactly one proceeds. |
| Four one-line public methods | Each one is just a predicate plus a renderer. The predicates are **mutually exclusive and exhaustive**, which is what guarantees progress. |

## Why `notify()` deadlocks here: a concrete trace

```mermaid
sequenceDiagram
    participant F as fizz (waiting)
    participant B as buzz (waiting)
    participant N as number
    N->>N: prints 4, current=5
    N->>F: notify() wakes fizz (arbitrary choice!)
    F->>F: 5 is not mine → wait()
    Note over B: buzz owns 5 but was never woken
    Note over F,N: everyone is waiting → hang
```

## Run it

```bash
java FizzBuzzMultithreaded.java
```

```text
1 2 Fizz 4 Buzz Fizz 7 8 Fizz Buzz 11 Fizz 13 14 FizzBuzz 16 17 Fizz 19 Buzz Fizz 22 23 Fizz Buzz 26 Fizz 28 29 FizzBuzz
PASS
```

## Common mistakes

* Overlapping predicates (`i % 3 == 0` for fizz *and* `i % 15 == 0` for fizzbuzz): two threads both think it's their turn.
* Loop condition `while (current <= n)` **outside** the synchronized block: you read `current` without the lock.
* Using `notify()`.

## Follow-up questions

??? question "How would you avoid waking all four threads each time?"
    Use one `Condition` per role (as in [problem 2](02-round-robin.md)). After printing `current`, compute which role owns `current+1` and
    signal only that condition.

??? question "Solve it with semaphores."
    Give each role its own semaphore, with `number` starting at 1. A dispatcher loop (or the number thread) decides who owns the next number and releases that
    semaphore. Each printer does `acquire → print → release(dispatcher)`.

??? example "Full source: FizzBuzzMultithreaded.java"
    ```java
    --8<-- "examples/interview-coding/FizzBuzzMultithreaded.java"
    ```
