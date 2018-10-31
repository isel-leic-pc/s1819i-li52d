package sketches;

import pt.isel.pc.examples.utils.Timeouts;

import java.util.concurrent.atomic.AtomicInteger;

public class OptimizedSemaphore {

    private final AtomicInteger units;
    private volatile int waiting = 0;

    private final Object mon = new Object();

    public OptimizedSemaphore(int initialUnits) {
        units = new AtomicInteger(initialUnits);
    }

    private boolean tryAcquire() {
        int observedUnits;
        do {
            observedUnits = units.get();
            if (observedUnits == 0) return false;
        } while (!units.compareAndSet(observedUnits, observedUnits - 1));
        return true;
    }

    public boolean acquire(long timeout) throws InterruptedException {
        // outside any lock
        if (tryAcquire()) {
            // fast path
            return true;
        }
        if(Timeouts.noWait(timeout)) {
            return false;
        }
        long limit = Timeouts.start(timeout);
        synchronized (mon) {
            waiting += 1;
            try {
                while (!tryAcquire()) {
                    long remaining = Timeouts.remaining(limit);
                    if (Timeouts.isTimeout(remaining)) {
                        return false;
                    }
                    try {
                        mon.wait(remaining);
                    } catch (InterruptedException e) {
                        if (units.get() > 0 && waiting > 1) {
                            mon.notify();
                        }
                        throw e;
                    }
                }
            }finally {
                waiting -= 1;
            }
            return true;
        }
    }

    public void release() {
        units.incrementAndGet();
        if(waiting == 0) {
            return;
        }
        synchronized (mon) {
            if(waiting != 0) {
                mon.notify();
            }
        }
    }

}
