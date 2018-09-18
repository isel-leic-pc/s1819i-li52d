package pt.isel.pc.examples.synchronizers;

import pt.isel.pc.examples.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleNarySemaphore {

    private long units;
    private final Lock mon = new ReentrantLock();
    private final Condition cond = mon.newCondition();

    public SimpleNarySemaphore(long initial) {
        if (initial < 0) {
            throw new IllegalArgumentException("initial units must not be negative");
        }
        units = initial;
    }

    public boolean acquire(long requested, long timeout) throws InterruptedException {
        if (requested <= 0) {
            throw new IllegalArgumentException("requested units must be greater than zero");
        }
        try {
            mon.lock();
            if (units > requested) {
                units -= requested;
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            while (true) {
                cond.await(remaining, TimeUnit.MILLISECONDS);
                if (units > requested) {
                    units -= requested;
                    return true;
                }
                remaining = Timeouts.remaining(t);
                if (Timeouts.isTimeout(remaining)) {
                    return false;
                }
            }
        } finally {
            mon.unlock();
        }
    }

    public void release(long released) {
        try {
            mon.lock();
            units += released;
            cond.signalAll();
        } finally {
            mon.unlock();
        }
    }

}
