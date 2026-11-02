package com.playbook.tools;

/** Deliberately NOT thread-safe: Lincheck finds the interleaving that loses an update. */
public class BrokenCounter {
    private volatile int value;                   // volatile gives visibility, NOT atomicity

    public int incrementAndGet() { return ++value; }

    public int get() { return value; }
}
