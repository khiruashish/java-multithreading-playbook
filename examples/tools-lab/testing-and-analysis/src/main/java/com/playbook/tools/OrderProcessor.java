package com.playbook.tools;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/** Asynchronous worker: callers submit and return immediately; a virtual thread processes in the background. */
public class OrderProcessor implements AutoCloseable {
    private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
    private final Set<String> processed = ConcurrentHashMap.newKeySet();
    private final Thread worker = Thread.ofVirtual().name("order-processor").start(this::loop);

    public void submit(String orderId) { inbox.add(orderId); }

    public boolean isProcessed(String orderId) { return processed.contains(orderId); }

    public int processedCount() { return processed.size(); }

    private void loop() {
        try {
            while (true) {
                String id = inbox.take();
                Thread.sleep(20);                 // simulated work
                processed.add(id);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override public void close() { worker.interrupt(); }
}
