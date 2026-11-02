package com.playbook.tools;

import com.google.errorprone.annotations.concurrent.GuardedBy;

/** Error Prone checks @GuardedBy at COMPILE time. withdrawUnsafe() accesses balance without the lock → build fails. */
public class GuardedAccount {
    private final Object lock = new Object();

    @GuardedBy("lock")
    private long balanceMinor;

    public void deposit(long amount) {
        synchronized (lock) { balanceMinor += amount; }   // OK: lock held
    }

    public void withdrawUnsafe(long amount) {
        balanceMinor -= amount;                           // ERROR: [GuardedBy] lock not held
    }
}
