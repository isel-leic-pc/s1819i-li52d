package pt.isel.pc.examples.intro;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;

public class SafetyHazards {

    private static final Logger log = LoggerFactory.getLogger(SafetyHazards.class);

    private int counter = 0;

    @Test
    public void missing_increments() throws InterruptedException {
        int nOfReps = 2000;
        int nOfThreads = 20;
        List<Thread> ths = new LinkedList<>();
        for (int i = 0; i < nOfThreads; ++i) {
            Thread th = new Thread(() -> {
                for (int j = 0; j < nOfReps; ++j) {
                    counter += 1;
                }
            });
            th.start();
            ths.add(th);
        }
        for (Thread th : ths) {
            th.join();
        }
        assertEquals(nOfReps * nOfThreads, counter);
    }

    @Test
    public void check_then_act() throws InterruptedException {
        final Map<String, AtomicInteger> map = Collections.synchronizedMap(new HashMap<>());
        Lock lock = new ReentrantLock();

        int nOfReps = 2000;
        int nOfUrls = 20;
        int nOfThreads = 20;

        List<Thread> ths = new LinkedList<>();
        for (int i = 0; i < nOfThreads; ++i) {
            Thread th = new Thread(() -> {

                for (int j = 0; j < nOfReps; ++j) {
                    for (int k = 0; k < nOfUrls; ++k) {
                        String url = String.format("/some/path/%d", k);
                        try {
                            lock.lock();
                            AtomicInteger counter = map.get(url);
                            if (counter == null) {
                                log.info("adding counter for url {}", url);
                                counter = new AtomicInteger(0);
                                map.put(url, counter);
                            }
                            counter.incrementAndGet();
                        } finally {
                            lock.unlock();
                        }
                    }
                }
            });
            th.start();
            ths.add(th);
        }
        for (Thread th : ths) {
            th.join();
        }
        assertEquals(nOfUrls, map.entrySet().size());
        for (Map.Entry<String, AtomicInteger> entry : map.entrySet()) {
            assertEquals(nOfReps * nOfThreads, entry.getValue().intValue());
        }
    }
}
