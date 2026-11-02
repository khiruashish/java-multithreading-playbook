import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/** Run: java MillionThreads.java  — 1,000,000 tasks that each block for 1 s. */
public class MillionThreads {
    public static void main(String[] args) {
        int tasks = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000;
        long start = System.nanoTime();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, tasks).forEach(i -> ex.submit(() -> {
                Thread.sleep(Duration.ofSeconds(1));
                return i;
            }));
        }
        System.out.printf("%,d virtual threads, each sleeping 1s, finished in %,d ms%n",
                tasks, (System.nanoTime() - start) / 1_000_000);
        System.out.printf("A 200-thread platform pool would need ~%,d s%n", tasks / 200);
    }
}
