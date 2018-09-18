package pt.isel.pc.examples.synchronizers;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;

public class SimpleNarySemaphoreTests {

    private static Logger logger = LoggerFactory.getLogger(SimpleNarySemaphoreTests.class);

    @Test
    public void semaphore_limits_the_granted_units() throws InterruptedException {
        int nOfThreads = 10;
        int nOfReps = 10;
        long timeout = Long.MAX_VALUE;
        int initial = 4;

        AtomicBoolean error = new AtomicBoolean(false);
        AtomicInteger counter = new AtomicInteger(initial);
        List<Thread> ths = new LinkedList<>();
        SimpleNarySemaphore sem = new SimpleNarySemaphore(initial);
        for (int i = 0; i < nOfThreads; ++i) {
            Thread th = new Thread(() -> {
                for (int j = 0; j < nOfReps; ++j) {
                    try {
                        sem.acquire(2, timeout);
                        logger.info("after acquire");
                        int current = counter.addAndGet(-2);
                        if(current < 0 || current > 2) {
                            logger.error("invalid units {}", current);
                            error.set(true);
                        }
                        Thread.sleep(100);
                        if(current < 0 || current > 2) {
                            logger.error("invalid units {}", current);
                            error.set(true);
                        }
                        sem.release(1);
                        current = counter.addAndGet(1);
                        if(current < 0 || current > 3) {
                            logger.error("invalid units {}", current);
                            error.set(true);
                        }
                        Thread.sleep(100);
                        if(current < 0 || current > 3) {
                            logger.error("invalid units {}", current);
                            error.set(true);
                        }
                        current = counter.addAndGet(1);
                        logger.info("before release");
                        sem.release(1);

                    } catch (InterruptedException e) {
                        error.set(true);
                    }

                }
            });
            th.start();
            ths.add(th);
        }
        for (Thread th : ths) {
            th.join();
        }
        assertFalse(error.get());
    }

}
