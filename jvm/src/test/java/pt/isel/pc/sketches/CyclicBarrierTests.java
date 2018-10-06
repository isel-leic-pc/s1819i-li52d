package pt.isel.pc.sketches;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.Helper;
import pt.isel.pc.examples.synchronizers.CyclicBarrier;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CyclicBarrierTests {

    private static final Logger log = LoggerFactory.getLogger(CyclicBarrierTests.class);

    @Test
    public void barrier_is_cyclic_and_only_opens_when_all_threads_are_there() throws Exception {
        int nOfThreads = 7;
        int nOfReps = 100;
        AtomicInteger counter = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(nOfThreads);
        Helper h = new Helper();
        for(int i = 0; i<nOfThreads ; ++i) {
            h.createAndStart(() -> {
                for(int j = 0 ; j<nOfReps ; ++j) {
                    log.info("entering barrier {}", j);
                    counter.addAndGet(1);
                    barrier.await(Long.MAX_VALUE);
                    log.info("leaving barrier {}", j);
                    assertTrue(counter.get() >= nOfThreads * j);
                }
            });
        }
        h.join();
    }

    @Test
    public void barrier_breaks_on_first_timeout() throws Exception {
        int nOfThreads = 7;
        int nOfReps = 100;
        CyclicBarrier barrier = new CyclicBarrier(nOfThreads);
        Helper h = new Helper();
        for(int i = 0; i<nOfThreads ; ++i) {
            int tix = i;
            h.createAndStart(() -> {
                for(int j = 0 ; j<nOfReps ; ++j) {
                    if(tix == 0 && j == nOfReps/2) {
                        Thread.sleep(2000);
                    }
                    try {
                        log.info("entering barrier {}", j);
                        barrier.await(1000);
                        log.info("leaving barrier {}", j);
                        assertTrue(j != nOfReps/2);
                    } catch(BrokenBarrierException | TimeoutException e) {
                        assertEquals(nOfReps/2, j);
                        break;
                    }
                }
            });
        }
        h.join();
    }

}
