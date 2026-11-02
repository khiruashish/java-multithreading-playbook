import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe money transfer between accounts: no lost updates, no deadlock, total money conserved.
 * Money is held in minor units (paise) as long — never double.
 * Run: java BankTransfer.java
 */
public class BankTransfer {

    // --8<-- [start:account]
    static final class Account {
        private final long id;
        private final ReentrantLock lock = new ReentrantLock();
        private long balanceMinor;                       // guarded by lock

        Account(long id, long openingMinor) { this.id = id; this.balanceMinor = openingMinor; }

        long balance() {
            lock.lock();
            try { return balanceMinor; } finally { lock.unlock(); }
        }
    }
    // --8<-- [end:account]

    // --8<-- [start:transfer]
    static final AtomicLong rejected = new AtomicLong();

    /** @return true if moved, false if insufficient funds */
    static boolean transfer(Account from, Account to, long amountMinor) {
        if (amountMinor <= 0) throw new IllegalArgumentException("amount must be positive");
        if (from == to) return true;                     // same lock twice would be fine (reentrant) but pointless

        Account first = from.id < to.id ? from : to;     // global lock order by id → no deadlock cycle
        Account second = first == from ? to : from;

        first.lock.lock();
        try {
            second.lock.lock();
            try {
                if (from.balanceMinor < amountMinor) {   // check-then-act is SAFE: both locks are held
                    rejected.incrementAndGet();
                    return false;
                }
                from.balanceMinor -= amountMinor;
                to.balanceMinor += amountMinor;
                return true;
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }
    // --8<-- [end:transfer]

    // --8<-- [start:audit]
    /** Consistent snapshot of the total: lock ALL accounts in id order, then sum. */
    static long auditTotal(Account[] accounts) {
        for (Account a : accounts) a.lock.lock();        // accounts[] is sorted by id
        try {
            long total = 0;
            for (Account a : accounts) total += a.balanceMinor;
            return total;
        } finally {
            for (int i = accounts.length - 1; i >= 0; i--) accounts[i].lock.unlock();
        }
    }
    // --8<-- [end:audit]

    public static void main(String[] args) throws Exception {
        int n = 50;
        Account[] accounts = new Account[n];
        for (int i = 0; i < n; i++) accounts[i] = new Account(i, 1_000_00);    // ₹1,000.00 each
        long before = auditTotal(accounts);

        long[] midAudit = new long[1];
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < 200_000; t++) {
                ex.submit(() -> {
                    var rnd = ThreadLocalRandom.current();
                    transfer(accounts[rnd.nextInt(n)], accounts[rnd.nextInt(n)], rnd.nextLong(1, 50_00));
                });
                if (t == 100_000) ex.submit(() -> midAudit[0] = auditTotal(accounts));
            }
        }
        long after = auditTotal(accounts);
        System.out.printf("200,000 concurrent transfers: total before=%d after=%d mid-run audit=%d, rejected(insufficient)=%d%n",
                before, after, midAudit[0], rejected.get());
        if (before != after || midAudit[0] != before) throw new AssertionError("money created or destroyed!");
        for (Account a : accounts) if (a.balance() < 0) throw new AssertionError("negative balance");
        System.out.println("PASS");
    }
}
