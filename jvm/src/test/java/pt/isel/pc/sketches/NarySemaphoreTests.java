package pt.isel.pc.sketches;

import org.junit.Test;
import pt.isel.pc.Helper;
import pt.isel.pc.examples.synchronizers.NarySemaphore;
import sketches.SimpleNarySemaphoreFifoWithExplicitMonitors;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

public class NarySemaphoreTests {

    private void threadMethod(NarySemaphore sem, int maxUnits, AtomicInteger counter)
            throws InterruptedException {
        Random rg = new Random();

        while (true) {
            int requestUnits = rg.nextInt(maxUnits) + 1;
            sem.tryAcquire(requestUnits, Long.MAX_VALUE);
            try {
                counter.addAndGet(-requestUnits);
                assertTrue(counter.get() >= 0);
                Thread.sleep(10);
            } finally {
                counter.addAndGet(requestUnits);
                assertTrue(counter.get() <= maxUnits);
                sem.release(requestUnits);
            }
        }
    }

    private void firstTest(NarySemaphore sem, int maxUnits, AtomicInteger counter)
            throws InterruptedException {
        int nOfThreads = 20;
        Helper h = new Helper();
        for (int i = 0; i < nOfThreads; ++i) {
            h.createAndStart(() -> threadMethod(sem, maxUnits, counter));
        }
        Thread.sleep(10_000);
        h.interruptAndJoin();
    }

    @Test
    public void first_test() throws InterruptedException {

        int nOfUnits = 10;
        AtomicInteger counter = new AtomicInteger(nOfUnits);
        NarySemaphore sem = new SimpleNarySemaphoreFifoWithExplicitMonitors(nOfUnits);
        firstTest(sem, nOfUnits, counter);
    }
}
