import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sleeping barber (Dijkstra): one barber, N waiting chairs. If no chair is free the customer leaves.
 * If nobody is waiting the barber sleeps. Classic semaphore signalling problem.
 * Run: java SleepingBarber.java
 */
public class SleepingBarber {

    // --8<-- [start:core]
    private final int chairs;
    private int waiting;                                       // guarded by seatsLock
    private final ReentrantLock seatsLock = new ReentrantLock();
    private final Semaphore customersReady = new Semaphore(0); // counts waiting customers; barber sleeps on it
    private final Semaphore barberReady = new Semaphore(0);    // barber signals "next please"
    final AtomicInteger served = new AtomicInteger();
    final AtomicInteger turnedAway = new AtomicInteger();

    SleepingBarber(int chairs) { this.chairs = chairs; }

    void barber() {
        try {
            while (true) {
                customersReady.acquire();                      // sleep until a customer sits down
                seatsLock.lock();
                try {
                    waiting--;                                 // take one customer from the waiting room
                } finally {
                    seatsLock.unlock();
                }
                barberReady.release();                         // invite that customer into the chair
                Thread.sleep(Duration.ofMillis(2));            // cut hair (outside the lock!)
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();                // shop closes
        }
    }

    void customer() throws InterruptedException {
        seatsLock.lock();
        try {
            if (waiting == chairs) {                           // no free chair → leave
                turnedAway.incrementAndGet();
                return;
            }
            waiting++;                                         // sit down in the waiting room
        } finally {
            seatsLock.unlock();
        }
        customersReady.release();                              // wake the barber if he's asleep
        barberReady.acquire();                                 // wait to be called
        served.incrementAndGet();                              // getting a haircut
    }
    // --8<-- [end:core]

    public static void main(String[] args) throws Exception {
        var shop = new SleepingBarber(5);
        Thread barber = Thread.ofVirtual().name("barber").start(shop::barber);
        List<Thread> customers = new ArrayList<>();
        int total = 300;
        for (int i = 0; i < total; i++) {
            customers.add(Thread.ofVirtual().start(() -> {
                try { shop.customer(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            Thread.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextInt(0, 4)));   // random arrivals
        }
        for (Thread c : customers) c.join();
        barber.interrupt();
        barber.join();
        System.out.printf("customers=%d served=%d turnedAway=%d%n", total, shop.served.get(), shop.turnedAway.get());
        if (shop.served.get() + shop.turnedAway.get() != total || shop.served.get() == 0) throw new AssertionError();
        System.out.println("PASS");
    }
}
