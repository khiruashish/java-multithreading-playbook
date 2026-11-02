import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Run: java RaceConditionDemo.java  — lost updates with a plain int, and three fixes. */
public class RaceConditionDemo {
    static int unsafe;
    static int locked;
    static final Object LOCK = new Object();
    static final AtomicInteger atomic = new AtomicInteger();
    static final LongAdder adder = new LongAdder();

    public static void main(String[] args) {
        int n = 100_000;
        try (var ex = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < n; i++) {
                ex.submit(() -> {
                    unsafe++;                                  // read-modify-write race
                    synchronized (LOCK) { locked++; }          // fix 1
                    atomic.incrementAndGet();                  // fix 2
                    adder.increment();                         // fix 3 (best under contention)
                });
            }
        }
        System.out.printf("expected=%d unsafe=%d synchronized=%d atomic=%d longAdder=%d%n",
                n, unsafe, locked, atomic.get(), adder.sum());
    }
}
