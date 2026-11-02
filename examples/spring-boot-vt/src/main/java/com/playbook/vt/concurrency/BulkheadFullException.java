package com.playbook.vt.concurrency;

/** Thrown when a bulkhead cannot grant a permit within its max-wait. Mapped to HTTP 503. */
public class BulkheadFullException extends RuntimeException {
    private final String bulkhead;

    public BulkheadFullException(String bulkhead) {
        super("Bulkhead '" + bulkhead + "' is full");
        this.bulkhead = bulkhead;
    }

    public String bulkhead() {
        return bulkhead;
    }
}
