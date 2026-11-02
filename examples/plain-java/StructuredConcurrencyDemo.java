import java.time.Duration;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * JDK 21-24 preview API. Run on JDK 21:
 *   java --enable-preview --source 21 StructuredConcurrencyDemo.java
 * (On JDK 25+ the API changed to StructuredTaskScope.open(Joiner) — see docs/modern/structured-concurrency.md.)
 */
public class StructuredConcurrencyDemo {

    record User(String name) {}
    record Order(String id) {}

    static User findUser() throws InterruptedException {
        Thread.sleep(Duration.ofMillis(200));
        return new User("asha");
    }

    static Order fetchOrder(boolean fail) throws InterruptedException {
        Thread.sleep(Duration.ofMillis(50));
        if (fail) throw new IllegalStateException("order-service down");
        return new Order("o-1");
    }

    static String handle(boolean fail) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Subtask<User> user = scope.fork(StructuredConcurrencyDemo::findUser);
            Subtask<Order> order = scope.fork(() -> fetchOrder(fail));
            scope.join().throwIfFailed();
            return user.get() + " " + order.get();
        }
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        System.out.println("success: " + handle(false) + " in " + (System.nanoTime() - t0) / 1_000_000 + " ms");

        t0 = System.nanoTime();
        try {
            handle(true);
        } catch (Exception e) {
            System.out.println("failure: " + e.getCause().getMessage() + " after "
                    + (System.nanoTime() - t0) / 1_000_000 + " ms (findUser was cancelled, not awaited)");
        }
    }
}
