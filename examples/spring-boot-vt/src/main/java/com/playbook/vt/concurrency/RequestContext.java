package com.playbook.vt.concurrency;

import java.util.concurrent.Callable;

/**
 * Request-scoped context with an explicit, bounded lifetime.
 *
 * <p>This sample targets Java 21 without preview features, so it is backed by a ThreadLocal that is ALWAYS cleared in
 * {@code finally}. On Java 25 swap the body for {@code ScopedValue} (final, JEP 506); callers do not change:
 *
 * <pre>{@code
 * private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();
 * public static <T> T callWith(String id, Callable<T> c) throws Exception {
 *     return ScopedValue.where(REQUEST_ID, id).call(c::call);
 * }
 * public static String requestId() { return REQUEST_ID.orElse("none"); }
 * }</pre>
 */
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private RequestContext() {
    }

    public static <T> T callWith(String requestId, Callable<T> body) throws Exception {
        String previous = REQUEST_ID.get();
        REQUEST_ID.set(requestId);
        try {
            return body.call();
        } finally {
            if (previous == null) REQUEST_ID.remove();
            else REQUEST_ID.set(previous);
        }
    }

    public static String requestId() {
        String id = REQUEST_ID.get();
        return id == null ? "none" : id;
    }

    /** Wraps a task so the caller's context is re-bound inside a forked virtual thread. */
    public static <T> Callable<T> propagate(Callable<T> task) {
        String captured = REQUEST_ID.get();
        return captured == null ? task : () -> callWith(captured, task);
    }
}
