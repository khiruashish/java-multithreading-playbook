import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * LeetCode 1195. Four threads: one prints "Fizz" (div by 3 only), one "Buzz" (div by 5 only), one "FizzBuzz"
 * (div by 15), one prints the number. Together they must produce the normal FizzBuzz sequence.
 * Run: java FizzBuzzMultithreaded.java
 */
public class FizzBuzzMultithreaded {

    // --8<-- [start:core]
    private final int n;
    private int current = 1;                                  // guarded by this
    private final List<String> out;

    FizzBuzzMultithreaded(int n, List<String> out) { this.n = n; this.out = out; }

    /** Generic "my turn?" loop shared by all four threads. Only the predicate and the printer differ. */
    private synchronized void loop(IntPredicate mine, IntFunction<String> render) throws InterruptedException {
        while (true) {
            while (current <= n && !mine.test(current)) wait();
            if (current > n) { notifyAll(); return; }
            out.add(render.apply(current));
            current++;
            notifyAll();                                      // 4 threads, 4 conditions: notify() could wake the wrong one
        }
    }

    void fizz()     throws InterruptedException { loop(i -> i % 3 == 0 && i % 5 != 0, i -> "Fizz"); }
    void buzz()     throws InterruptedException { loop(i -> i % 5 == 0 && i % 3 != 0, i -> "Buzz"); }
    void fizzbuzz() throws InterruptedException { loop(i -> i % 15 == 0,              i -> "FizzBuzz"); }
    void number()   throws InterruptedException { loop(i -> i % 3 != 0 && i % 5 != 0, Integer::toString); }
    // --8<-- [end:core]

    interface Body { void run() throws InterruptedException; }

    public static void main(String[] args) throws Exception {
        int n = 30;
        List<String> out = new ArrayList<>();
        var fb = new FizzBuzzMultithreaded(n, out);
        List<Body> bodies = List.of(fb::fizz, fb::buzz, fb::fizzbuzz, fb::number);
        List<Thread> threads = new ArrayList<>();
        for (Body b : bodies) {
            threads.add(Thread.ofVirtual().start(() -> {
                try { b.run(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
        }
        for (Thread t : threads) t.join();
        System.out.println(String.join(" ", out));
        for (int i = 1; i <= n; i++) {
            String exp = i % 15 == 0 ? "FizzBuzz" : i % 3 == 0 ? "Fizz" : i % 5 == 0 ? "Buzz" : Integer.toString(i);
            if (!out.get(i - 1).equals(exp)) throw new AssertionError(i + ": " + out.get(i - 1));
        }
        System.out.println("PASS");
    }
}
