import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Thread-safe LRU cache with a fixed capacity.
 * Run: java ConcurrentLruCache.java
 */
public class ConcurrentLruCache<K, V> {

    // --8<-- [start:core]
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<K, V> map;              // guarded by lock

    public ConcurrentLruCache(int capacity) {
        this.capacity = capacity;
        // accessOrder=true: every get() moves the entry to the tail → head is the least-recently used
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > ConcurrentLruCache.this.capacity;   // evict after insertion overflows
            }
        };
    }

    public V get(K key) {
        lock.lock();                                    // even get() needs the lock: it MUTATES the access order
        try { return map.get(key); } finally { lock.unlock(); }
    }

    public void put(K key, V value) {
        lock.lock();
        try { map.put(key, value); } finally { lock.unlock(); }
    }

    /** Load outside the lock so a slow loader never blocks other keys. Two threads may load the same key. */
    public V getOrLoad(K key, Function<K, V> loader) {
        V v = get(key);
        if (v != null) return v;
        V loaded = loader.apply(key);                   // potentially slow IO, no lock held
        lock.lock();
        try {
            V raced = map.get(key);                     // someone else loaded meanwhile? keep theirs
            if (raced != null) return raced;
            map.put(key, loaded);
            return loaded;
        } finally { lock.unlock(); }
    }

    public int size() {
        lock.lock();
        try { return map.size(); } finally { lock.unlock(); }
    }
    // --8<-- [end:core]

    public static void main(String[] args) throws Exception {
        var lru = new ConcurrentLruCache<Integer, String>(3);
        lru.put(1, "a"); lru.put(2, "b"); lru.put(3, "c");
        lru.get(1);                                     // 1 becomes most recently used
        lru.put(4, "d");                                // evicts 2 (the LRU), not 1
        if (lru.get(2) != null || !"a".equals(lru.get(1)) || lru.size() != 3) throw new AssertionError("LRU order");
        System.out.println("sequential LRU semantics ok: 2 evicted, 1 kept");

        var cache = new ConcurrentLruCache<Integer, Integer>(100);
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < 50_000; t++) {
                ex.submit(() -> {
                    int k = ThreadLocalRandom.current().nextInt(500);
                    Integer v = cache.getOrLoad(k, key -> key * 2);
                    if (v != k * 2) throw new AssertionError("wrong value");
                });
            }
        }
        System.out.println("50,000 concurrent getOrLoad calls: size=" + cache.size() + " (never above capacity 100)");
        if (cache.size() != 100) throw new AssertionError();
        System.out.println("PASS");
    }
}
