package com.playbook.vt.concurrency;

/** Thrown when a fan-out does not finish within its deadline. Mapped to HTTP 504. */
public class DeadlineExceededException extends RuntimeException {
    public DeadlineExceededException(String message) {
        super(message);
    }
}
