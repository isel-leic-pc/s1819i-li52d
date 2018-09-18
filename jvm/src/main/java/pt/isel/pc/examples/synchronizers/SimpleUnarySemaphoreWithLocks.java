package pt.isel.pc.examples.synchronizers;


import pt.isel.pc.examples.utils.Timeouts;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleUnarySemaphoreWithLocks implements UnarySemaphore {

    private long units;
    private final Lock mon = new ReentrantLock();
    private final Condition cond = mon.newCondition();

    public SimpleUnarySemaphoreWithLocks(long initial) {
        if (initial < 0) {
            throw new IllegalArgumentException("initial units must not be negative");
        }
        units = initial;
    }

    public boolean acquire(long timeout) throws InterruptedException {
        try {
            mon.lock();
            if (units > 0) {
                units -= 1;
                return true;
            }
            if (Timeouts.noWait(timeout)) {
                return false;
            }
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            while (true) {
                try {
                    cond.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (units > 0) {
                        mon.notify();
                    }
                    throw e;
                }
                if (units > 0) {
                    units -= 1;
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

    public void release() {
        try {
            mon.lock();
            units += 1;
            cond.signal();
        } finally {
            mon.unlock();
        }
    }
}
