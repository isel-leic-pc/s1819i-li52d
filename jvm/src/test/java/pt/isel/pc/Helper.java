package pt.isel.pc;

import sun.jvm.hotspot.utilities.AssertionFailure;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertFalse;

public class Helper {

    @FunctionalInterface
    public interface InterruptibleRunnable {
        void run() throws Exception;
    }

    private List<Thread> ths = new LinkedList<>();
    private ConcurrentLinkedQueue<AssertionError> failures = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Exception> errors = new ConcurrentLinkedQueue<>();

    public void createAndStart(InterruptibleRunnable runnable) {
        Thread th = new Thread(() -> {
            try {
                runnable.run();
            }catch (InterruptedException e) {
                // ignore
            }catch(AssertionError e) {
                failures.add(e);
            }catch(Exception e) {
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
        if(!failures.isEmpty()) {
            throw failures.peek();
        }
        if(!errors.isEmpty()) {
            throw new UnexpectedExceptionError(errors.peek());
        }
    }

    public void join() throws InterruptedException {
        for (Thread th : ths) {
            th.join();
        }
        if(!failures.isEmpty()) {
            throw failures.peek();
        }
        if(!errors.isEmpty()) {
            throw new UnexpectedExceptionError(errors.peek());
        }
    }

}
