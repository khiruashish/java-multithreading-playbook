package com.playbook.vt.concurrency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;

/**
 * Streams the JFR {@code jdk.VirtualThreadPinned} event in-process and exports it as a metric.
 *
 * <p>On JDK 21-23 this catches blocking inside {@code synchronized}. On JDK 24+ (JEP 491) it should only fire for
 * native frames or class initialisers. Alert on {@code jvm.virtual_threads.pinned} &gt; 0.
 */
public class PinningMonitor implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PinningMonitor.class);

    private final Counter pinned;
    private final Duration threshold;
    private RecordingStream stream;

    public PinningMonitor(MeterRegistry registry, Duration threshold) {
        this.pinned = Counter.builder("jvm.virtual_threads.pinned")
                .description("Virtual threads that blocked while pinned to their carrier")
                .register(registry);
        this.threshold = threshold;
    }

    @Override
    public void afterPropertiesSet() {
        stream = new RecordingStream();
        stream.enable("jdk.VirtualThreadPinned").withThreshold(threshold).withStackTrace();
        stream.onEvent("jdk.VirtualThreadPinned", event -> {
            pinned.increment();
            if (event.getStackTrace() != null) {
                String top = event.getStackTrace().getFrames().stream()
                        .filter(RecordedFrame::isJavaFrame)
                        .map(f -> f.getMethod().getType().getName() + "." + f.getMethod().getName()
                                + ":" + f.getLineNumber())
                        .filter(s -> !s.startsWith("java.") && !s.startsWith("jdk."))
                        .findFirst().orElse("<jdk>");
                log.warn("Virtual thread pinned for {} ms at {}", event.getDuration().toMillis(), top);
            }
        });
        stream.startAsync();
    }

    @Override
    public void destroy() {
        if (stream != null) stream.close();
    }
}
