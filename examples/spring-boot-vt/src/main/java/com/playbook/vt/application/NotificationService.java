package com.playbook.vt.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;

/**
 * With spring.threads.virtual.enabled=true, Spring Boot backs @Async with a SimpleAsyncTaskExecutor that starts a
 * new virtual thread per call, and @Scheduled with a SimpleAsyncTaskScheduler on virtual threads. No pool to size.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final LongAdder sent = new LongAdder();

    @Async
    public CompletableFuture<String> sendOrderConfirmation(String orderId) throws InterruptedException {
        Thread.sleep(Duration.ofMillis(50)); // simulated SMTP/SMS call: the VT simply parks
        sent.increment();
        return CompletableFuture.completedFuture("sent:" + orderId + " on virtual=" + Thread.currentThread().isVirtual());
    }

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT60S")
    public void reportStats() {
        log.info("notifications sent so far: {} (scheduler thread virtual={})",
                sent.sum(), Thread.currentThread().isVirtual());
    }

    public long sentCount() {
        return sent.sum();
    }
}
