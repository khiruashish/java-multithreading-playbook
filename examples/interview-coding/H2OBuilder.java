import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * LeetCode 1117. Hydrogen and oxygen threads arrive randomly; they must leave in groups of exactly 2 H + 1 O.
 * Run: java H2OBuilder.java
 */
public class H2OBuilder {

    // --8<-- [start:core]
    private final Semaphore hydrogenSlots = new Semaphore(2);   // at most 2 H in the current molecule
    private final Semaphore oxygenSlots = new Semaphore(1);     // at most 1 O in the current molecule
    private final CyclicBarrier molecule = new CyclicBarrier(3); // the 3 atoms wait for each other

    void hydrogen(Runnable releaseHydrogen) throws Exception {
        hydrogenSlots.acquire();          // 1. claim a seat in the molecule being formed
        try {
            molecule.await();             // 2. wait until 2 H + 1 O are seated
            releaseHydrogen.run();        // 3. "bond" (print)
        } finally {
            hydrogenSlots.release();      // 4. free the seat for the NEXT molecule
        }
    }

    void oxygen(Runnable releaseOxygen) throws Exception {
        oxygenSlots.acquire();
        try {
            molecule.await();
            releaseOxygen.run();
        } finally {
            oxygenSlots.release();
        }
    }
    // --8<-- [end:core]

    public static void main(String[] args) throws Exception {
        int molecules = 500;
        List<Character> atoms = new ArrayList<>();
        for (int i = 0; i < molecules; i++) { atoms.add('H'); atoms.add('H'); atoms.add('O'); }
        Collections.shuffle(atoms);

        List<Character> out = Collections.synchronizedList(new ArrayList<>());
        var h2o = new H2OBuilder();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (char a : atoms) {
                ex.submit(() -> {
                    if (a == 'H') h2o.hydrogen(() -> out.add('H'));
                    else h2o.oxygen(() -> out.add('O'));
                    return null;
                });
            }
        }
        for (int i = 0; i < out.size(); i += 3) {
            long h = out.subList(i, i + 3).stream().filter(c -> c == 'H').count();
            if (h != 2) throw new AssertionError("group " + i / 3 + " = " + out.subList(i, i + 3));
        }
        System.out.println(molecules + " molecules, every consecutive group of 3 is 2xH + 1xO, e.g. "
                + out.subList(0, 9));
        System.out.println("PASS");
    }
}
