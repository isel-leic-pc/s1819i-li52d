package pt.isel.pc;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertFalse;

public class Helper {

    @FunctionalInterface
    public interface InterruptibleRunnable {
        void run() throws InterruptedException;
    }

    private List<Thread> ths = new LinkedList<>();
    private ConcurrentLinkedQueue<AssertionError> errors = new ConcurrentLinkedQueue<>();

    public void createAndStart(InterruptibleRunnable runnable) {
        Thread th = new Thread(() -> {
            try {
                runnable.run();
            }catch (InterruptedException e) {
                // ignore
            }catch(AssertionError e) {
                errors.add(e);
            }
        });
        th.start();
        ths.add(th);
    }

    public void interruptAndJoin() throws InterruptedException {
        for (Thread th : ths) {
            th.interrupt();
            th.join(2000);
            assertFalse("thread should have stopped", th.isAlive());
        }
        if(!errors.isEmpty()) {
            throw errors.peek();
        }
    }

    public void join() throws InterruptedException {
        for (Thread th : ths) {
            th.join();
        }
        if(!errors.isEmpty()) {
            throw errors.peek();
        }
    }

}
