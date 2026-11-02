import java.util.concurrent.StructuredTaskScope;

/**
 * Preview on JDK 21-24, final on JDK 25. Run on JDK 21:
 *   java --enable-preview --source 21 ScopedValueDemo.java
 */
public class ScopedValueDemo {

    static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    static void service() throws Exception {
        log("service start");
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {   // JDK 21-24 API
            scope.fork(() -> { log("child: fetch user"); return null; });   // inherits the binding
            scope.fork(() -> { log("child: fetch cart"); return null; });
            scope.join().throwIfFailed();
        }
        ScopedValue.where(REQUEST_ID, "req-42/retry").run(() -> log("nested rebinding"));
        log("service end");
    }

    static void log(String msg) {
        System.out.printf("[%s] %-22s on %s%n", REQUEST_ID.orElse("unbound"), msg,
                Thread.currentThread().isVirtual() ? "virtual thread" : "platform thread");
    }

    public static void main(String[] args) throws Exception {
        ScopedValue.where(REQUEST_ID, "req-42").call(() -> { service(); return null; });
        log("after scope");
    }
}
