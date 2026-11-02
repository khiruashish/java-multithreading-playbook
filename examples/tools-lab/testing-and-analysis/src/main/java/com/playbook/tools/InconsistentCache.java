package com.playbook.tools;

import java.util.HashMap;
import java.util.Map;

/**
 * Deliberately buggy: {@code size} is written under the lock everywhere, but size() reads it WITHOUT the lock.
 * A caller may see a stale value forever (no happens-before). SpotBugs reports IS2_INCONSISTENT_SYNC.
 */
public class InconsistentCache {
    private final Map<String, String> map = new HashMap<>();
    private int size;

    public synchronized void put(String k, String v) {
        if (map.put(k, v) == null) size++;
    }

    public synchronized void remove(String k) {
        if (map.remove(k) != null) size--;
    }

    public synchronized void clear() {
        map.clear();
        size = 0;
    }

    public synchronized String get(String k) { return map.get(k); }

    public int size() { return size; }            // BUG: unsynchronized read of a lock-guarded field
}
