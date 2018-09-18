package pt.isel.pc.examples.synchronizers;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;

public class SimpleUnarySemaphoreTests {

    private static Logger logger = LoggerFactory.getLogger(SimpleUnarySemaphoreTests.class);

    private int acquireAndCheckElapsed(UnarySemaphore sem, long timeout) throws InterruptedException {
        boolean success;
        int tries = 0;
        long allowedTimeError = 10;

        do {
            long start = System.currentTimeMillis();
            success = sem.acquire(timeout);
            long delta = System.currentTimeMillis() - start;
            tries += 1;

            if (Math.abs(delta - timeout) > allowedTimeError) {
                logger.error("acquire was {} but should not exceed {}", delta, timeout);
                if (success) {
                    sem.release();
                }
                return -1;
            }
        } while (!success);

        return tries;
    }

    public void test(UnarySemaphore sem) throws InterruptedException {

        int nOfThreads = 10;
        int nOfReps = 100;
        long timeout = 10;
        // a simple holder for a mutable boolean
        final boolean[] error = new boolean[1];

        List<Thread> ths = new ArrayList<>();
        final AtomicBoolean check = new AtomicBoolean();

        for (int i = 0; i < nOfThreads; ++i) {
            Thread th = new Thread(() -> {
                try {
                    for (int j = 0; j < nOfReps; ++j) {
                        int tries = acquireAndCheckElapsed(sem, timeout);
                        if (tries < 0) {
                            error[0] = true;
                            return;
                        }
                        boolean old = false;
                        try {
                            old = check.getAndSet(true);
                            logger.info("Acquired {} after {} tries", j, tries);
                            Thread.sleep(100);
                        } finally {
                            check.set(false);
                            sem.release();
                            logger.info("Released {}", j);
                            if (old != false) {
                                logger.error("Another thread was inside the semaphore");
                                error[0] = true;
                                return;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    logger.info("interruped, giving up");
                }
            });
            th.start();
            ths.add(th);
        }

        // let's interrupt some threads to see what happens
        for (int i = 0; i < nOfThreads; i += 3) {
            ths.get(i).interrupt();
            Thread.sleep(1000);
        }

        // join then all
        for (Thread th : ths) {
            th.join();
        }
        assertFalse(error[0]);
    }

    @Test
    public void test_monitor_based_semaphore() throws InterruptedException {
        test(new SimpleUnarySemaphoreWithImplicitMonitors(1));
    }

    @Test
    public void test_lock_based_semaphore() throws InterruptedException {
        test(new SimpleUnarySemaphoreWithLocks(1));
    }

}
