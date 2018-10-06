package pt.isel.pc.sketches;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.isel.pc.Helper;
import pt.isel.pc.examples.synchronizers.ReaderWriterLock;
import pt.isel.pc.examples.synchronizers.SimpleReaderWriterLock;
import pt.isel.pc.examples.synchronizers.SimpleReaderWriterLockWithBatchQueue;
import sketches.FirstReaderWriterLock;
import sketches.ReaderWriterLockWithBatch;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ReaderWriterLockTests {

    private static final Logger log = LoggerFactory.getLogger(ReaderWriterLockTests.class);

    @Test
    public void test_SimpleReaderWriterLock() throws InterruptedException {
        test(new SimpleReaderWriterLock());
    }

    @Test
    public void test_SimpleReaderWriterLockWithBatchQueue() throws InterruptedException {
        test(new SimpleReaderWriterLockWithBatchQueue());
    }

    @Test
    public void test_FirstReaderWriterLock() throws InterruptedException {
        test(new FirstReaderWriterLock());
    }

    @Test
    public void test_ReaderWriterLockWithBatch() throws InterruptedException {
        test(new ReaderWriterLockWithBatch());
    }

    public void test(ReaderWriterLock lock) throws InterruptedException {

        Helper helper = new Helper();
        int nOfReaders = 10;
        int nOfWriters = 5;
        int nOfReps = 1000;
        AtomicInteger readingCount = new AtomicInteger(0);
        AtomicBoolean isWriting = new AtomicBoolean();
        for(int i = 0 ; i<nOfReaders ; ++i) {
            helper.createAndStart(() -> {
                Random r = new Random();
                for(int j = 0; j<nOfReps ; ++j) {
                    log.info("reader before start");
                    lock.startRead();
                    log.info("reader after start");

                    assertFalse(isWriting.get());
                    readingCount.incrementAndGet();
                    Thread.sleep(r.nextInt(5));
                    readingCount.decrementAndGet();

                    lock.endRead();
                }
            });
        }

        for(int i = 0 ; i<nOfWriters ; ++i) {
            helper.createAndStart(() -> {
                Random r = new Random();
                for(int j = 0; j<nOfReps ; ++j) {
                    log.info("writer before start");
                    lock.startWrite();
                    log.info("writer after start");

                    assertFalse(isWriting.get());
                    assertEquals(0, readingCount.get());
                    isWriting.set(true);
                    Thread.sleep(r.nextInt(5));
                    isWriting.set(false);

                    lock.endWrite();
                }
            });
        }

        helper.join();
    }
}
