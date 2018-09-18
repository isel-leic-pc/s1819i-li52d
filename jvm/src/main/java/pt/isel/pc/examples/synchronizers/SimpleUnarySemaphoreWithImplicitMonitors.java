package pt.isel.pc.examples.synchronizers;


import pt.isel.pc.examples.utils.Timeouts;

public class SimpleUnarySemaphoreWithImplicitMonitors implements UnarySemaphore {

    private long units;
    private final Object mon = new Object();

    public SimpleUnarySemaphoreWithImplicitMonitors(long initial) {
        if (initial < 0) {
            throw new IllegalArgumentException("initial units must not be negative");
        }
        units = initial;
    }

    @Override
    public boolean acquire(long timeout) throws InterruptedException {
        synchronized (mon) {
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
                    mon.wait(remaining);
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
        }
    }

    @Override
    public void release() {
        synchronized (mon) {
            units += 1;
            mon.notify();
        }
    }
}
