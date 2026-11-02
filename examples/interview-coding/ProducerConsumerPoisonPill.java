import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-stage pipeline: reader → parser workers → aggregator, connected by bounded queues and shut down with
 * poison pills. Uses virtual threads, so blocking put/take is cheap.
 * Run: java ProducerConsumerPoisonPill.java
 */
public class ProducerConsumerPoisonPill {

    // --8<-- [start:types]
    sealed interface Msg permits Line, Poison {}
    record Line(String text) implements Msg {}
    record Poison() implements Msg {}                       // "no more data" marker
    static final Poison POISON = new Poison();
    // --8<-- [end:types]

    // --8<-- [start:pipeline]
    static long run(List<String> input, int parsers) throws Exception {
        BlockingQueue<Msg> lines = new ArrayBlockingQueue<>(100);     // stage 1 → 2 (bounded = backpressure)
        BlockingQueue<Long> numbers = new ArrayBlockingQueue<>(100);  // stage 2 → 3
        final long END = Long.MIN_VALUE;                               // poison for stage 3
        AtomicLong total = new AtomicLong();

        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            // Stage 1: single producer
            ex.submit(() -> {
                for (String s : input) lines.put(new Line(s));
                for (int i = 0; i < parsers; i++) lines.put(POISON);   // ONE pill per consumer
                return null;
            });

            // Stage 2: N parallel parsers
            AtomicInteger finishedParsers = new AtomicInteger();
            for (int p = 0; p < parsers; p++) {
                ex.submit(() -> {
                    while (true) {
                        Msg m = lines.take();
                        if (m instanceof Poison) break;                 // swallow my pill and stop
                        if (m instanceof Line(String text)) numbers.put(Long.parseLong(text.trim()));
                    }
                    if (finishedParsers.incrementAndGet() == parsers) {    // exactly ONE parser sees this
                        numbers.put(END);                                   // last parser out poisons stage 3
                    }
                    return null;
                });
            }

            // Stage 3: single aggregator
            ex.submit(() -> {
                long sum = 0;
                for (long n; (n = numbers.take()) != END; ) sum += n;
                total.set(sum);
                return null;
            });
        }                                                              // close() waits for all stages
        return total.get();
    }
    // --8<-- [end:pipeline]

    public static void main(String[] args) throws Exception {
        int n = 100_000;
        List<String> input = java.util.stream.IntStream.rangeClosed(1, n).mapToObj(i -> " " + i + " ").toList();
        long sum = run(input, 8);
        long expected = (long) n * (n + 1) / 2;
        System.out.printf("pipeline sum=%d expected=%d%n", sum, expected);
        if (sum != expected) throw new AssertionError();
        System.out.println("PASS");
    }
}
