import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * LeetCode 1114. Three threads call first(), second(), third() in ANY order; the output must be first, second, third.
 * Run: java PrintInOrder.java
 */
public class PrintInOrder {

    // --8<-- [start:latch]
    static final class Foo {
        private final CountDownLatch firstDone = new CountDownLatch(1);
        private final CountDownLatch secondDone = new CountDownLatch(1);

        void first(Runnable printFirst) {
            printFirst.run();
            firstDone.countDown();                     // opens gate 1 (and publishes everything before it)
        }

        void second(Runnable printSecond) throws InterruptedException {
            firstDone.await();                         // blocks until gate 1 is open
            printSecond.run();
            secondDone.countDown();
        }

        void third(Runnable printThird) throws InterruptedException {
            secondDone.await();
            printThird.run();
        }
    }
    // --8<-- [end:latch]

    // --8<-- [start:cf]
    /** Same ordering expressed as a dependency chain of CompletableFutures. */
    static final class FooCf {
        private final CompletableFuture<Void> firstDone = new CompletableFuture<>();
        private final CompletableFuture<Void> secondDone = new CompletableFuture<>();

        void first(Runnable r)  { r.run(); firstDone.complete(null); }
        void second(Runnable r) { firstDone.join(); r.run(); secondDone.complete(null); }
        void third(Runnable r)  { secondDone.join(); r.run(); }
    }
    // --8<-- [end:cf]

    public static void main(String[] args) throws Exception {
        for (int round = 0; round < 200; round++) {             // repeat to shake out races
            List<String> out = new CopyOnWriteArrayList<>();
            var foo = new Foo();
            Thread t3 = Thread.ofVirtual().start(() -> uncheck(() -> foo.third(() -> out.add("third"))));
            Thread t2 = Thread.ofVirtual().start(() -> uncheck(() -> foo.second(() -> out.add("second"))));
            Thread t1 = Thread.ofVirtual().start(() -> foo.first(() -> out.add("first")));
            t1.join(); t2.join(); t3.join();
            if (!out.equals(List.of("first", "second", "third"))) throw new AssertionError(out);

            List<String> out2 = new CopyOnWriteArrayList<>();
            var cf = new FooCf();
            Thread a = Thread.ofVirtual().start(() -> cf.third(() -> out2.add("third")));
            Thread b = Thread.ofVirtual().start(() -> cf.second(() -> out2.add("second")));
            Thread c = Thread.ofVirtual().start(() -> cf.first(() -> out2.add("first")));
            a.join(); b.join(); c.join();
            if (!out2.equals(List.of("first", "second", "third"))) throw new AssertionError(out2);
        }
        System.out.println("200 rounds, threads started in reverse order, always: first second third");
        System.out.println("PASS");
    }

    interface Body { void run() throws InterruptedException; }

    static void uncheck(Body b) {
        try { b.run(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
