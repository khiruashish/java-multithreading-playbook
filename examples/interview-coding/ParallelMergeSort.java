import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Parallel merge sort with Fork/Join (divide and conquer, work stealing).
 * Run: java ParallelMergeSort.java
 */
public class ParallelMergeSort {

    // --8<-- [start:core]
    static final class SortTask extends RecursiveAction {
        private static final int SEQUENTIAL_THRESHOLD = 8_192;    // below this, forking costs more than it saves
        private final int[] a;
        private final int[] tmp;                                  // shared scratch buffer, disjoint ranges per task
        private final int lo, hi;                                 // sorts a[lo, hi)

        SortTask(int[] a, int[] tmp, int lo, int hi) {
            this.a = a; this.tmp = tmp; this.lo = lo; this.hi = hi;
        }

        @Override
        protected void compute() {
            if (hi - lo <= SEQUENTIAL_THRESHOLD) {
                Arrays.sort(a, lo, hi);                           // leaf: sequential sort
                return;
            }
            int mid = (lo + hi) >>> 1;                            // >>> avoids int overflow for huge arrays
            invokeAll(new SortTask(a, tmp, lo, mid),              // fork left, run right, join both
                      new SortTask(a, tmp, mid, hi));
            merge(mid);                                           // both halves are sorted now
        }

        private void merge(int mid) {
            System.arraycopy(a, lo, tmp, lo, hi - lo);
            int i = lo, j = mid, k = lo;
            while (i < mid && j < hi) a[k++] = tmp[i] <= tmp[j] ? tmp[i++] : tmp[j++];   // <= keeps it stable
            while (i < mid) a[k++] = tmp[i++];
            while (j < hi) a[k++] = tmp[j++];
        }
    }

    static void parallelSort(int[] a) {
        ForkJoinPool.commonPool().invoke(new SortTask(a, new int[a.length], 0, a.length));
    }
    // --8<-- [end:core]

    public static void main(String[] args) {
        int[] data = new Random(7).ints(20_000_000).toArray();
        int[] seq = data.clone(), par = data.clone();

        long t0 = System.nanoTime();
        sequentialMergeSort(seq, new int[seq.length], 0, seq.length);
        long seqMs = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        parallelSort(par);
        long parMs = (System.nanoTime() - t0) / 1_000_000;

        int[] ref = data.clone();
        Arrays.sort(ref);
        if (!Arrays.equals(par, ref) || !Arrays.equals(seq, ref)) throw new AssertionError("not sorted");
        System.out.printf("20M ints on %d cores: sequential merge sort %d ms, fork/join %d ms (%.1fx)%n",
                Runtime.getRuntime().availableProcessors(), seqMs, parMs, (double) seqMs / parMs);
        System.out.println("PASS");
    }

    static void sequentialMergeSort(int[] a, int[] tmp, int lo, int hi) {
        if (hi - lo <= 8_192) { Arrays.sort(a, lo, hi); return; }
        int mid = (lo + hi) >>> 1;
        sequentialMergeSort(a, tmp, lo, mid);
        sequentialMergeSort(a, tmp, mid, hi);
        new SortTask(a, tmp, lo, hi).merge(mid);
    }
}
