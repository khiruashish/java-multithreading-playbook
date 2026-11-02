package com.playbook.vt.concurrency;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Structured fan-out on virtual threads WITHOUT preview APIs.
 *
 * <p>Gives the guarantees of {@code StructuredTaskScope}: every subtask finishes (or is cancelled) before
 * {@link Scope#close()} returns, a failure of a required subtask cancels its siblings, and the whole fan-out
 * shares one deadline. When structured concurrency becomes final, only this class needs to change.
 */
public final class FanOut {

    private final ThreadFactory threadFactory;

    public FanOut(String threadNamePrefix) {
        this.threadFactory = Thread.ofVirtual().name(threadNamePrefix, 0).factory();
    }

    public Scope open(Duration deadline) {
        return new Scope(Executors.newThreadPerTaskExecutor(threadFactory), System.nanoTime() + deadline.toNanos());
    }

    /** A lexical scope that owns its subtasks. Always use with try-with-resources. */
    public static final class Scope implements AutoCloseable {
        private final ExecutorService executor;
        private final long deadlineNanos;
        private final List<Future<?>> forked = new ArrayList<>();

        private Scope(ExecutorService executor, long deadlineNanos) {
            this.executor = executor;
            this.deadlineNanos = deadlineNanos;
        }

        /** A subtask whose failure fails the whole scope. */
        public <T> Required<T> fork(Callable<T> task) {
            Future<T> f = executor.submit(task);
            forked.add(f);
            return new Required<>(f, this);
        }

        /** A subtask that falls back to a default value on failure or deadline. */
        public <T> Optional<T> forkOptional(Callable<T> task, Function<Throwable, T> fallback) {
            Future<T> f = executor.submit(task);
            forked.add(f);
            return new Optional<>(f, fallback, this);
        }

        long remainingNanos() {
            return Math.max(0, deadlineNanos - System.nanoTime());
        }

        void cancelAll() {
            forked.forEach(f -> f.cancel(true)); // interrupt: blocking IO in siblings aborts promptly
        }

        @Override
        public void close() {
            cancelAll();        // no-op for completed subtasks
            executor.close();   // waits for cancelled subtasks to actually finish: nothing outlives the scope
        }
    }

    public static final class Required<T> {
        private final Future<T> future;
        private final Scope scope;

        Required(Future<T> future, Scope scope) {
            this.future = future;
            this.scope = scope;
        }

        public T get() throws InterruptedException {
            try {
                return future.get(scope.remainingNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                scope.cancelAll();
                throw new DeadlineExceededException("required subtask exceeded the fan-out deadline");
            } catch (ExecutionException e) {
                scope.cancelAll();
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw new IllegalStateException("required subtask failed", e.getCause());
            } catch (InterruptedException e) {
                scope.cancelAll();
                throw e;
            }
        }
    }

    public static final class Optional<T> {
        private final Future<T> future;
        private final Function<Throwable, T> fallback;
        private final Scope scope;
        private volatile boolean degraded;

        Optional(Future<T> future, Function<Throwable, T> fallback, Scope scope) {
            this.future = future;
            this.fallback = fallback;
            this.scope = scope;
        }

        public T get() throws InterruptedException {
            try {
                return future.get(scope.remainingNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                degraded = true;
                return fallback.apply(e);
            } catch (ExecutionException e) {
                degraded = true;
                return fallback.apply(e.getCause());
            }
        }

        public boolean degraded() {
            return degraded;
        }
    }
}
