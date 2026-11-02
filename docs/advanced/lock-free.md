# Lock-free & Wait-free Programming

<span class="level l4">L4</span>

## Progress guarantees

| Guarantee | Meaning | Example |
|---|---|---|
| Blocking | A stalled thread can stop everyone | `synchronized`, `ReentrantLock` |
| Obstruction-free | A thread running alone finishes | Some STM designs |
| **Lock-free** | *Some* thread always makes progress | CAS loops: `ConcurrentLinkedQueue`, `AtomicLong.incrementAndGet` |
| **Wait-free** | *Every* thread finishes in bounded steps | `AtomicLong.getAndAdd` on x86 (`LOCK XADD`), `LongAdder.increment` mostly |

## Treiber stack (lock-free)

```java
public final class TreiberStack<E> {
    private record Node<E>(E item, Node<E> next) {}
    private final AtomicReference<Node<E>> top = new AtomicReference<>();

    public void push(E item) {
        Node<E> oldTop, newTop;
        do {
            oldTop = top.get();
            newTop = new Node<>(item, oldTop);
        } while (!top.compareAndSet(oldTop, newTop));   // retry if another thread won
    }

    public E pop() {
        Node<E> oldTop;
        do {
            oldTop = top.get();
            if (oldTop == null) return null;
        } while (!top.compareAndSet(oldTop, oldTop.next()));
        return oldTop.item();
    }
}
```

The garbage collector makes ABA harmless here: a node can't be reused while a thread still holds a reference to it.
In C/C++ you'd need hazard pointers or epoch-based reclamation.

## Michael–Scott queue

`ConcurrentLinkedQueue` implements it: two CAS-updated pointers (`head` and `tail`), where a lagging `tail` is "helped" forward by
other threads. Study the JDK source for a production-quality example, including the `VarHandle` relaxed reads.

## When **not** to go lock-free

* Multi-variable invariants: a lock is far simpler and usually fast enough.
* Under heavy contention, CAS retry storms can be **slower** than a lock that parks waiters.
* Readability and reviewability: few engineers can verify a lock-free algorithm. Prove it with jcstress.

## Single-writer principle

The fastest concurrent code avoids contention entirely: **one thread owns each piece of mutable state**, and others
communicate with it through queues. This is the core idea of LMAX Disruptor, actors, and Kafka partitions. With VTs, a
"single writer per key" lane is cheap (see [Case Studies](../system-design/case-studies.md)).
